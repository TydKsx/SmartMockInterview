package com.sdumagicode.backend.controller.topic;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sdumagicode.backend.core.result.GlobalResult;
import com.sdumagicode.backend.core.result.GlobalResultGenerator;
import com.sdumagicode.backend.core.service.log.annotation.VisitLogger;
import com.sdumagicode.backend.dto.ArticleDTO;
import com.sdumagicode.backend.entity.Topic;
import com.sdumagicode.backend.service.ArticleService;
import com.sdumagicode.backend.service.TopicService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author ronger
 */
@RestController
@RequestMapping("/api/v1/topic")
public class TopicController {
    @Resource
    private ArticleService articleService;
    @Resource
    private TopicService topicService;

    @GetMapping("/topic-nav")
    public GlobalResult topicNav() {
        List<Topic> topics = topicService.findTopicNav();
        return GlobalResultGenerator.genSuccessResult(topics);
    }

    @GetMapping("/{name}")
    @VisitLogger
    public GlobalResult<PageInfo<ArticleDTO>> articles(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer rows, @PathVariable String name) {
        PageHelper.startPage(page, rows);
        List<ArticleDTO> list = articleService.findArticlesByTopicUri(name);
        PageInfo<ArticleDTO> pageInfo = new PageInfo(list);
        return GlobalResultGenerator.genSuccessResult(pageInfo);
    }
}
