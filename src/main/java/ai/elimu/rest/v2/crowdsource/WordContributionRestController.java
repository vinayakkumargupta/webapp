package ai.elimu.rest.v2.crowdsource;
import ai.elimu.dao.*;
import ai.elimu.model.content.LetterToAllophoneMapping;
import ai.elimu.model.content.Word;
import ai.elimu.model.contributor.Contributor;
import ai.elimu.model.contributor.WordContributionEvent;
import ai.elimu.model.v2.gson.content.LetterToAllophoneMappingGson;
import ai.elimu.model.v2.gson.content.WordGson;
import ai.elimu.model.v2.gson.crowdsource.WordContributionEventGson;
import ai.elimu.rest.v2.JpaToGsonConverter;
import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/**
 * REST API for the Crowdsource application: https://github.com/elimu-ai/crowdsource
 * <p>
 * 
 * This controller is responsible for handling the creation of words contributed through the Crowdsource app
 */
@RestController
@RequestMapping(value = "/rest/v2/crowdsource/word-contributions", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class WordContributionRestController {

    private final Logger logger = LogManager.getLogger();

    @Autowired
    private WordDao wordDao;

    @Autowired
    private WordContributionEventDao wordContributionEventDao;

    @Autowired
    private ContributorDao contributorDao;

    @Autowired
    private LetterToAllophoneMappingDao letterToAllophoneMappingDao;
    
    /**
     * Returns a list of {@link LetterToAllophoneMapping}s that will be used to construct a {@link Word}.
     */
    @RequestMapping(value = "/letter-to-allophone-mappings", method = RequestMethod.GET)
    public String getLetterToAllophoneMappings(HttpServletRequest request, HttpServletResponse response) {
        logger.info("getLetterToAllophoneMappings");

        JSONArray letterToAllophoneMappingsJsonArray = new JSONArray();
        for (LetterToAllophoneMapping letterToAllophoneMapping : letterToAllophoneMappingDao.readAllOrderedByUsage()) {
            LetterToAllophoneMappingGson letterToAllophoneMappingGson =
                    JpaToGsonConverter.getLetterToAllophoneMappingGson(letterToAllophoneMapping);
            String json = new Gson().toJson(letterToAllophoneMappingGson);
            letterToAllophoneMappingsJsonArray.put(new JSONObject(json));
        }

        String jsonResponse = letterToAllophoneMappingsJsonArray.toString();
        logger.info("jsonResponse: " + jsonResponse);
        return jsonResponse;
    }

    /**
     * Handles the creation of a new {@link Word} & the corresponding {@link WordContributionEvent}.
     *
     * @param requestBody JSON should contain fields required for the creation of a {@link WordContributionEventGson} 
     * object.
     */
    @RequestMapping(value = "/word", method = RequestMethod.POST)
    public String postWordContribution(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody String requestBody
    ) {
        logger.info("postWordContribution");

        // Validate the Contributor.
        JSONObject jsonObject = new JSONObject();

        String providerIdGoogle = request.getHeader("providerIdGoogle");
        logger.info("providerIdGoogle: " + providerIdGoogle);
        if (StringUtils.isBlank(providerIdGoogle)) {
            jsonObject.put("result", "error");
            jsonObject.put("errorMessage", "Missing providerIdGoogle");
            response.setStatus(HttpStatus.BAD_REQUEST.value());

            String jsonResponse = jsonObject.toString();
            logger.info("jsonResponse: " + jsonResponse);
            return jsonResponse;
        }

        // Lookup the Contributor by ID
        Contributor contributor = contributorDao.readByProviderIdGoogle(providerIdGoogle);
        logger.info("contributor: " + contributor);
        if (contributor == null) {
            jsonObject.put("result", "error");
            jsonObject.put("errorMessage", "The Contributor was not found.");
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());

            String jsonResponse = jsonObject.toString();
            logger.info("jsonResponse: " + jsonResponse);
            return jsonResponse;
        }

        logger.info("requestBody: " + requestBody);

        // Convert the request body to a WordContributionEventGson
        WordContributionEventGson wordContributionEventGson =
                new Gson().fromJson(requestBody, WordContributionEventGson.class);
        logger.info("wordContributionEventGson: " + wordContributionEventGson);

        // Extract the WordGson from the WordContributionEventGson
        WordGson wordGson = wordContributionEventGson.getWord();
        logger.info("wordGson: " + wordGson);

        // Check if the word is already existing.
        Word existingWord = wordDao.readByText(wordGson.getText().toLowerCase());
        if (existingWord != null) {
            jsonObject.put("result", "error");
            jsonObject.put("errorMessage", "NonUnique");
            response.setStatus(HttpStatus.CONFLICT.value());

            String jsonResponse = jsonObject.toString();
            logger.info("jsonResponse: " + jsonResponse);
            return jsonResponse;
        }

        try {
            // Convert the WordGson to Word POJO.
            Word word = new Word();
            word.setWordType(wordGson.getWordType());
            word.setText(wordGson.getText().toLowerCase());
            List<LetterToAllophoneMappingGson> letterToAllophoneMappingsGsons = wordGson.getLetterToAllophoneMappings();
            List<LetterToAllophoneMapping> letterToAllophoneMappings = new ArrayList<>();
            for (LetterToAllophoneMappingGson letterToAllophoneMappingGson : letterToAllophoneMappingsGsons) {
                LetterToAllophoneMapping letterToAllophoneMapping =
                        letterToAllophoneMappingDao.read(letterToAllophoneMappingGson.getId());
                letterToAllophoneMappings.add(letterToAllophoneMapping);
            }
            word.setLetterToAllophoneMappings(letterToAllophoneMappings);
            wordDao.create(word);

            WordContributionEvent wordContributionEvent = new WordContributionEvent();
            wordContributionEvent.setContributor(contributor);
            wordContributionEvent.setTime(wordContributionEventGson.getTime());
            wordContributionEvent.setWord(word);
            wordContributionEvent.setRevisionNumber(word.getRevisionNumber());
            wordContributionEvent.setComment(wordContributionEventGson.getComment());
            wordContributionEvent.setTimeSpentMs(System.currentTimeMillis() -
                    wordContributionEvent.getTime().getTimeInMillis());

            // TODO: wordContributionEvent.setTimeSpentMs(wordContributionEventGson.getTimeSpentMs());
            //  refer to: https://github.com/elimu-ai/webapp/pull/1289#discussion_r642024541

            // TODO: wordContributionEvent.setPlatform(Platform.CROWDSOURCE_APP);
            //  refer to : https://github.com/elimu-ai/webapp/pull/1289#discussion_r638936145

            wordContributionEventDao.create(wordContributionEvent);

            response.setStatus(HttpStatus.CREATED.value());
        } catch (Exception ex) {
            logger.error(ex);

            jsonObject.put("result", "error");
            jsonObject.put("errorMessage", ex.getMessage());
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        String jsonResponse = jsonObject.toString();
        logger.info("jsonResponse: " + jsonResponse);
        return jsonResponse;
    }
}
