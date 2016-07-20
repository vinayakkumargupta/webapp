package org.literacyapp.web.content.allophone;

import java.net.URLEncoder;
import java.util.Calendar;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import org.apache.commons.lang.StringUtils;

import org.apache.log4j.Logger;
import org.literacyapp.dao.AllophoneDao;
import org.literacyapp.model.Contributor;
import org.literacyapp.model.content.Allophone;
import org.literacyapp.model.contributor.ContentCreationEvent;
import org.literacyapp.model.enums.Environment;
import org.literacyapp.model.enums.Team;
import org.literacyapp.util.SlackApiHelper;
import org.literacyapp.web.context.EnvironmentContextLoaderListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/content/allophone/edit")
public class AllophoneEditController {
    
    private final Logger logger = Logger.getLogger(getClass());
    
    @Autowired
    private AllophoneDao allophoneDao;

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String handleRequest(Model model, @PathVariable Long id) {
    	logger.info("handleRequest");
        
        Allophone allophone = allophoneDao.read(id);
        model.addAttribute("allophone", allophone);

        return "content/allophone/edit";
    }
    
    @RequestMapping(value = "/{id}", method = RequestMethod.POST)
    public String handleSubmit(
            @PathVariable Long id,
            @Valid Allophone allophone,
            BindingResult result,
            Model model,
            HttpSession session
    ) {
    	logger.info("handleSubmit");
        
        if (StringUtils.isNotBlank(allophone.getValueIpa())) {
            if (allophone.getValueIpa().length() > 3) {
                result.rejectValue("valueIpa", "TooLong");
            }
            
            Allophone existingAllophone = allophoneDao.readByValueIpa(allophone.getLocale(), allophone.getValueIpa());
            if ((existingAllophone != null) && !existingAllophone.getId().equals(allophone.getId())) {
                result.rejectValue("valueIpa", "NonUnique");
            }
        }
        
        if (StringUtils.isNotBlank(allophone.getValueSampa())) {
            if (allophone.getValueSampa().length() > 3) {
                result.rejectValue("valueSampa", "TooLong");
            }

            Allophone existingAllophone = allophoneDao.readByValueSampa(allophone.getLocale(), allophone.getValueSampa());
            if ((existingAllophone != null) && !existingAllophone.getId().equals(allophone.getId())) {
                result.rejectValue("valueSampa", "NonUnique");
            }
        }
        
        if (result.hasErrors()) {
            model.addAttribute("allophone", allophone);
            return "content/allophone/edit";
        } else {
            allophoneDao.update(allophone);
            
            Contributor contributor = (Contributor) session.getAttribute("contributor");
            
            ContentCreationEvent contentCreationEvent = new ContentCreationEvent();
            contentCreationEvent.setContributor(contributor);
            contentCreationEvent.setContent(allophone);
            contentCreationEvent.setCalendar(Calendar.getInstance());
            
            if (EnvironmentContextLoaderListener.env == Environment.PROD) {
                String text = URLEncoder.encode(
                        contentCreationEvent.getContributor().getFirstName() + " just edited an Allophone (speech sound):\n" + 
                        "• Language: \"" + allophone.getLocale().getLanguage() + "\"\n" + 
                        "• IPA: \"" + allophone.getValueIpa() + "\"\n" + 
                        "• X-SAMPA: \"" + allophone.getValueSampa() + "\"\n" + 
                        "See ") + "http://literacyapp.org/content/allophone/list";
                String iconUrl = contentCreationEvent.getContributor().getImageUrl();
                SlackApiHelper.postMessage(Team.CONTENT_CREATION, text, iconUrl, null);
            }
            
            return "redirect:/content/allophone/list";
        }
    }
}
