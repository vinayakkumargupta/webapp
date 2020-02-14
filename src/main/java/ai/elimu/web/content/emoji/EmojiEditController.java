package ai.elimu.web.content.emoji;

import java.util.Calendar;
import java.util.List;
import javax.validation.Valid;

import org.apache.log4j.Logger;
import ai.elimu.dao.AllophoneDao;
import ai.elimu.dao.EmojiDao;
import ai.elimu.dao.WordDao;
import ai.elimu.model.content.Allophone;
import ai.elimu.model.content.Emoji;
import ai.elimu.model.content.Word;
import ai.elimu.model.enums.Language;
import ai.elimu.util.ConfigHelper;
import java.util.Iterator;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/content/emoji/edit")
public class EmojiEditController {
    
    private final Logger logger = Logger.getLogger(getClass());
    
    @Autowired
    private EmojiDao emojiDao;
    
    @Autowired
    private AllophoneDao allophoneDao;
    
    @Autowired
    private WordDao wordDao;

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String handleRequest(
            Model model, 
            @PathVariable Long id) {
    	logger.info("handleRequest");
        
        Language language = Language.valueOf(ConfigHelper.getProperty("content.language"));
        
        Emoji emoji = emojiDao.read(id);
        model.addAttribute("emoji", emoji);
        
        List<Allophone> allophones = allophoneDao.readAllOrderedByUsage(language);
        model.addAttribute("allophones", allophones);
        
        List<Word> words = wordDao.readAllOrderedByUsage(language);
        model.addAttribute("words", words);

        return "content/emoji/edit";
    }
    
    @RequestMapping(value = "/{id}", method = RequestMethod.POST)
    public String handleSubmit(
            @Valid Emoji emoji,
            BindingResult result,
            Model model) {
    	logger.info("handleSubmit");
        
        Language language = Language.valueOf(ConfigHelper.getProperty("content.language"));
        
        Emoji existingEmoji = emojiDao.readByGlyph(emoji.getGlyph());
        if ((existingEmoji != null) && !existingEmoji.getId().equals(emoji.getId())) {
            result.rejectValue("glyph", "NonUnique");
        }
        
        if (result.hasErrors()) {
            model.addAttribute("emoji", emoji);
            
            List<Allophone> allophones = allophoneDao.readAllOrderedByUsage(language);
            model.addAttribute("allophones", allophones);
            
            List<Word> words = wordDao.readAllOrderedByUsage(language);
            model.addAttribute("words", words);
            
            return "content/emoji/edit";
        } else {
            emoji.setTimeLastUpdate(Calendar.getInstance());
            emoji.setRevisionNumber(emoji.getRevisionNumber() + 1);
            emojiDao.update(emoji);
            
            return "redirect:/content/emoji/list#" + emoji.getId();
        }
    }
    
    @RequestMapping(value = "/{id}/add-content-label", method = RequestMethod.POST)
    @ResponseBody
    public String handleAddContentLabelRequest(
            HttpServletRequest request,
            @PathVariable Long id) {
    	logger.info("handleAddContentLabelRequest");
        
        logger.info("id: " + id);
        Emoji emoji = emojiDao.read(id);
        
        String wordIdParameter = request.getParameter("wordId");
        logger.info("wordIdParameter: " + wordIdParameter);
        if (StringUtils.isNotBlank(wordIdParameter)) {
            Long wordId = Long.valueOf(wordIdParameter);
            Word word = wordDao.read(wordId);
            Set<Word> words = emoji.getWords();
            if (!words.contains(word)) {
                words.add(word);
                emoji.setRevisionNumber(emoji.getRevisionNumber() + 1);
                emojiDao.update(emoji);
            }
        }
        
        return "success";
    }
    
    @RequestMapping(value = "/{id}/remove-content-label", method = RequestMethod.POST)
    @ResponseBody
    public String handleRemoveContentLabelRequest(
            HttpServletRequest request,
            @PathVariable Long id) {
    	logger.info("handleRemoveContentLabelRequest");
        
        logger.info("id: " + id);
        Emoji emoji = emojiDao.read(id);
        
        String wordIdParameter = request.getParameter("wordId");
        logger.info("wordIdParameter: " + wordIdParameter);
        if (StringUtils.isNotBlank(wordIdParameter)) {
            Long wordId = Long.valueOf(wordIdParameter);
            Word word = wordDao.read(wordId);
            Set<Word> words = emoji.getWords();
            Iterator<Word> iterator = words.iterator();
            while (iterator.hasNext()) {
                Word existingWord = iterator.next();
                if (existingWord.getId().equals(word.getId())) {
                    iterator.remove();
                }
            }
            emoji.setRevisionNumber(emoji.getRevisionNumber() + 1);
            emojiDao.update(emoji);
        }
        
        return "success";
    }
}
