package com.sdumagicode.backend.service.impl;

import com.sdumagicode.backend.core.constant.NotificationConstant;
import com.sdumagicode.backend.core.exception.BusinessException;
import com.sdumagicode.backend.core.exception.ContentNotExistException;
import com.sdumagicode.backend.core.exception.UltraViresException;
import com.sdumagicode.backend.core.service.AbstractService;

import com.sdumagicode.backend.dto.chat.ChatRecordsDto;
import com.sdumagicode.backend.entity.*;
import com.sdumagicode.backend.entity.chat.ChatRecords;
import com.sdumagicode.backend.entity.chat.Interviewer;
import com.sdumagicode.backend.entity.chat.Branch;
import com.sdumagicode.backend.handler.event.ArticleDeleteEvent;
import com.sdumagicode.backend.handler.event.ArticleEvent;
import com.sdumagicode.backend.handler.event.ArticleStatusEvent;
import com.sdumagicode.backend.mapper.ArticleMapper;
import com.sdumagicode.backend.mapper.ChatMapper;
import com.sdumagicode.backend.mapper.ShareReferenceMapper;
import com.sdumagicode.backend.mapper.mongoRepo.BranchRepository;
import com.sdumagicode.backend.service.*;
import com.sdumagicode.backend.util.Html2TextUtil;
import com.sdumagicode.backend.util.Utils;
import com.sdumagicode.backend.util.XssUtils;
import com.sdumagicode.backend.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * @author ronger
 */
@Service
@Slf4j
public class ArticleServiceImpl extends AbstractService<Article> implements ArticleService {

    @Resource
    private ArticleMapper articleMapper;
    @Resource
    private TagService tagService;
    @Resource
    private UserService userService;
    @Resource
    private NotificationService notificationService;
    @Resource
    private ApplicationEventPublisher publisher;
    @Resource
    private BankAccountService bankAccountService;

    @Autowired
    InterviewerService interviewerService;
    @Autowired
    ChatService chatService;

    @Value("${resource.domain}")
    private String domain;

    private static final int MAX_PREVIEW = 200;
    private static final String DEFAULT_STATUS = "0";
    private static final String DEFAULT_TOPIC_URI = "news";

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    ShareReferenceMapper shareReferenceMapper;

    @Autowired
    ChatMapper chatMapper;

    @Autowired
    BranchRepository branchRepository;

    @Override
    public List<ArticleDTO> findArticles(ArticleSearchDTO searchDTO) {
        List<ArticleDTO> list;
        if (StringUtils.isNotBlank(searchDTO.getTopicUri()) && !DEFAULT_TOPIC_URI.equals(searchDTO.getTopicUri())) {
            list = articleMapper.selectArticlesByTopicUri(searchDTO.getTopicUri());
        } else {
            list = articleMapper.selectArticles(searchDTO.getSearchText(), searchDTO.getTag(), searchDTO.getTopicUri());
        }
        list.forEach(articleDTO -> genArticle(articleDTO, 0));
        return list;
    }

    @Override
    public ArticleDTO findArticleDTOById(Long id, Integer type) {
        ArticleDTO articleDTO = articleMapper.selectArticleDTOById(id, type);
        if (articleDTO == null) {
            return null;
        }
        genArticle(articleDTO, type);
        return articleDTO;
    }

    @Override
    public List<ArticleDTO> findArticlesByTopicUri(String name) {
        List<ArticleDTO> list = articleMapper.selectArticlesByTopicUri(name);
        list.forEach(articleDTO -> genArticle(articleDTO, 0));
        return list;
    }

    @Override
    public List<ArticleDTO> findArticlesByTagName(String name) {
        return articleMapper.selectArticlesByTagName(name);
    }

    @Override
    public List<ArticleDTO> findUserArticlesByIdUser(Long idUser) {
        List<ArticleDTO> list = articleMapper.selectUserArticles(idUser);
        list.forEach(articleDTO -> genArticle(articleDTO, 0));
        return list;
    }

    /**
     *
     * @param article 相较于原来增加了List<ChatRecordDto>和List<interviewer>,里面在保存时只有对应的chatId和interviewerId为必要
     * @param user
     * @return
     * @throws UnsupportedEncodingException
     */
    @Override
    @Transactional(rollbackFor = {UnsupportedEncodingException.class})
    public Long postArticle(ArticleDTO article, User user) throws UnsupportedEncodingException {
        boolean isUpdate = false;
        String articleTitle = article.getArticleTitle();
        String articleTags = article.getArticleTags();
        String articleContent = article.getArticleContent();
        String articleContentHtml = XssUtils.filterHtmlCode(article.getArticleContentHtml());
        String reservedTag = checkTags(articleTags);
        boolean notification = false;
        if (StringUtils.isNotBlank(reservedTag)) {
            boolean isAdmin = userService.hasAdminPermission(user.getEmail());
            if (!isAdmin) {
                throw new UltraViresException(StringEscapeUtils.unescapeJava(reservedTag) + "标签为系统保留标签!");
            } else {
                notification = true;
            }
        }
        Article newArticle;
        Long idArticle = article.getIdArticle();
        if (idArticle == null || idArticle == 0) {
            newArticle = new Article();
            newArticle.setArticleTitle(articleTitle);
            newArticle.setArticleAuthorId(user.getIdUser());
            newArticle.setArticleTags(articleTags);
            newArticle.setCreatedTime(new Date());
            newArticle.setUpdatedTime(newArticle.getCreatedTime());
            newArticle.setArticleStatus(article.getArticleStatus());
            articleMapper.insertSelective(newArticle);
            articleMapper.insertArticleContent(newArticle.getIdArticle(), articleContent, articleContentHtml);
            /**
             * 针对分享相关内容的逻辑
             */
            //绑定分享内容相关表
            List<ChatRecordsDto> chatRecordsList = article.getChatRecordsList();
            List<Interviewer> interviewerList = article.getInterviewerList();
            
            if (chatRecordsList != null && !chatRecordsList.isEmpty() || 
                interviewerList != null && !interviewerList.isEmpty()) {
                
                List<ShareReference> shareReferenceList = new ArrayList<>();
                
                if (chatRecordsList != null && !chatRecordsList.isEmpty()) {
                    List<ShareReference> chatShareList = chatRecordsList.stream().map((item) -> {
                        ShareReference shareReference = new ShareReference();
                        shareReference.setArticle(newArticle);
                        shareReference.setType(0);
                        //制作快照
                        ChatRecords chatRecords = chatService.deepCopy(item.getChatId(), 2L);
                        shareReference.setChatId(chatRecords.getChatId());
                        return shareReference;
                    }).collect(Collectors.toList());
                    shareReferenceList.addAll(chatShareList);
                }
                
                if (interviewerList != null && !interviewerList.isEmpty()) {
                    List<ShareReference> interviewerShareList = interviewerList.stream().map((item) -> {
                        ShareReference shareReference = new ShareReference();
                        shareReference.setArticle(newArticle);
                        shareReference.setType(1);
                        //制作快照
                        Interviewer interviewer = interviewerService.deepCopy(item.getInterviewerId(), 2L);
                        shareReference.setInterviewerId(interviewer.getInterviewerId());
                        return shareReference;
                    }).collect(Collectors.toList());
                    shareReferenceList.addAll(interviewerShareList);
                }
                
                if (!shareReferenceList.isEmpty()) {
                    shareReferenceMapper.batchInsertShareReferences(shareReferenceList);
                }
            }

        } else {
            newArticle = articleMapper.selectByPrimaryKey(idArticle);
            // 如果文章之前状态为草稿则应视为新发布文章
            if (DEFAULT_STATUS.equals(newArticle.getArticleStatus())) {
                isUpdate = true;
            }
            newArticle.setArticleTitle(articleTitle);
            newArticle.setArticleTags(articleTags);
            newArticle.setArticleStatus(article.getArticleStatus());
            newArticle.setUpdatedTime(new Date());
            articleMapper.updateArticleContent(newArticle.getIdArticle(), articleContent, articleContentHtml);
            /**
             * 针对分享相关内容的逻辑
             */
            // 更新分享内容相关表
            List<ChatRecordsDto> chatRecordsList = article.getChatRecordsList();
            List<Interviewer> interviewerList = article.getInterviewerList();

            //先删除相关快照
            List<ShareReference> shareReferences = shareReferenceMapper.selectShareReferencesByArticleId(newArticle.getIdArticle());
            if (shareReferences != null && !shareReferences.isEmpty()) {
                // 收集需要删除的聊天记录ID和面试官ID
                List<Long> chatIds = new ArrayList<>();
                List<String> interviewerIds = new ArrayList<>();
                
                for (ShareReference reference : shareReferences) {
                    if (reference.getType() == 0 && reference.getChatId() != null) {
                        chatIds.add(reference.getChatId());
                    } else if (reference.getType() == 1 && reference.getInterviewerId() != null) {
                        interviewerIds.add(reference.getInterviewerId());
                    }
                }
                
                // 批量删除聊天记录和面试官
                if (!chatIds.isEmpty()) {
                    chatService.batchDeleteChatRecords(chatIds);
                }
                
                if (!interviewerIds.isEmpty()) {
                    interviewerService.batchDeleteInterviewers(interviewerIds);
                }
            }
            
            // 再删除原有的关联关系
            shareReferenceMapper.deleteByArticleId(newArticle.getIdArticle());
            
            
            // 重新添加新的关联关系
            if (chatRecordsList != null && !chatRecordsList.isEmpty() ||
                    interviewerList != null && !interviewerList.isEmpty()) {

                List<ShareReference> shareReferenceList = new ArrayList<>();

                if (chatRecordsList != null && !chatRecordsList.isEmpty()) {
                    List<ShareReference> chatShareList = chatRecordsList.stream().map((item) -> {
                        ShareReference shareReference = new ShareReference();
                        shareReference.setArticle(newArticle);
                        shareReference.setType(0);
                        //制作快照
                        ChatRecords chatRecords = chatService.deepCopy(item.getChatId(), 2L);
                        shareReference.setChatId(chatRecords.getChatId());
                        return shareReference;
                    }).collect(Collectors.toList());
                    shareReferenceList.addAll(chatShareList);
                }

                if (interviewerList != null && !interviewerList.isEmpty()) {
                    List<ShareReference> interviewerShareList = interviewerList.stream().map((item) -> {
                        ShareReference shareReference = new ShareReference();
                        shareReference.setArticle(newArticle);
                        shareReference.setType(1);
                        //制作快照
                        Interviewer interviewer = interviewerService.deepCopy(item.getInterviewerId(), 2L);
                        shareReference.setInterviewerId(interviewer.getInterviewerId());
                        return shareReference;
                    }).collect(Collectors.toList());
                    shareReferenceList.addAll(interviewerShareList);
                }

                if (!shareReferenceList.isEmpty()) {
                    shareReferenceMapper.batchInsertShareReferences(shareReferenceList);
                }
            }
        }
        Long newArticleId = newArticle.getIdArticle();
        // 更新文章链接
        if (DEFAULT_STATUS.equals(newArticle.getArticleStatus())) {
            // 文章
            newArticle.setArticlePermalink(domain + "/article/" + newArticleId);
            newArticle.setArticleLink("/article/" + newArticleId);
        } else {
            // 草稿
            newArticle.setArticlePermalink(domain + "/draft/" + newArticleId);
            newArticle.setArticleLink("/draft/" + newArticleId);
        }

        if (StringUtils.isNotBlank(articleContentHtml)) {
            String previewContent = Html2TextUtil.getContent(articleContentHtml);
            if (previewContent.length() > MAX_PREVIEW) {
                previewContent = previewContent.substring(0, MAX_PREVIEW);
            }
            newArticle.setArticlePreviewContent(previewContent);
        }
        articleMapper.updateByPrimaryKeySelective(newArticle);
        // 更新标签
        tagService.saveTagArticle(newArticle, articleContentHtml, user.getIdUser());
        if (DEFAULT_STATUS.equals(newArticle.getArticleStatus())) {
            // 文章发布事件
            publisher.publishEvent(new ArticleEvent(newArticleId, newArticle.getArticleTitle(), isUpdate, notification, user.getNickname(), newArticle.getArticleAuthorId()));
        }
        return newArticleId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer delete(Long id) {
        // 判断是否有评论
        if (!articleMapper.existsCommentWithPrimaryKey(id)) {
            // 删除关联数据(作品集关联关系,标签关联关系)
            deleteLinkedData(id);
            // 删除文章
            int result = articleMapper.deleteByPrimaryKey(id);
            if (result > 0) {
                publisher.publishEvent(new ArticleDeleteEvent(id));
            }
            return result;
        } else {
            throw new BusinessException("已有评论的文章不允许删除!");
        }
    }

    private void deleteLinkedData(Long id) {
        // 删除关联作品集
        articleMapper.deleteLinkedPortfolioData(id);
        // 删除引用标签记录
        articleMapper.deleteTagArticle(id);
        // 删除文章内容表
        articleMapper.deleteArticleContent(id);
        
        // 删除相关快照
        List<ShareReference> shareReferences = shareReferenceMapper.selectShareReferencesByArticleId(id);
        if (shareReferences != null && !shareReferences.isEmpty()) {
            // 收集需要删除的聊天记录ID和面试官ID
            List<Long> chatIds = new ArrayList<>();
            List<String> interviewerIds = new ArrayList<>();
            
            for (ShareReference reference : shareReferences) {
                if (reference.getType() == 0 && reference.getChatId() != null) {
                    chatIds.add(reference.getChatId());
                } else if (reference.getType() == 1 && reference.getInterviewerId() != null) {
                    interviewerIds.add(reference.getInterviewerId());
                }
            }
            
            // 批量删除聊天记录和面试官
            if (!chatIds.isEmpty()) {
                chatService.batchDeleteChatRecords(chatIds);
            }
            
            if (!interviewerIds.isEmpty()) {
                interviewerService.batchDeleteInterviewers(interviewerIds);
            }
        }
        
        // 删除文章与聊天记录、面试官的关联关系
        shareReferenceMapper.deleteByArticleId(id);
        // 删除相关未读消息
        notificationService.deleteUnreadNotification(id, NotificationConstant.PostArticle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementArticleViewCount(Long id) {
        Article article = articleMapper.selectByPrimaryKey(id);
        Integer articleViewCount = article.getArticleViewCount() + 1;
        articleMapper.updateArticleViewCount(article.getIdArticle(), articleViewCount);
    }

    @Override
    public String share(Integer id, String account) {
        Article article = articleMapper.selectByPrimaryKey(id);
        return article.getArticlePermalink() + "?s=" + account;
    }

    @Override
    public List<ArticleDTO> findDrafts(Long userId) {
        List<ArticleDTO> list = articleMapper.selectDrafts(userId);
        list.forEach(articleDTO -> genArticle(articleDTO, 0));
        return list;
    }

    @Override
    public List<ArticleDTO> findArticlesByIdPortfolio(Long idPortfolio) {
        List<ArticleDTO> list = articleMapper.selectArticlesByIdPortfolio(idPortfolio);
        list.forEach(articleDTO -> genArticle(articleDTO, 0));
        return list;
    }

    @Override
    public List<ArticleDTO> selectUnbindArticles(Long idPortfolio, String searchText, Long idUser) {
        List<ArticleDTO> list = articleMapper.selectUnbindArticlesByIdPortfolio(idPortfolio, searchText, idUser);
        list.forEach(articleDTO -> genArticle(articleDTO, 0));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateTags(Long idArticle, String tags, Long userId) throws UnsupportedEncodingException {
        Article article = articleMapper.selectByPrimaryKey(idArticle);
        if (!Objects.nonNull(article)) {
            throw new ContentNotExistException("更新失败,文章不存在!");
        }
        article.setArticleTags(tags);
        articleMapper.updateArticleTags(idArticle, tags);
        tagService.saveTagArticle(article, "", userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePerfect(Long idArticle, String articlePerfect) {
        if (articleMapper.updatePerfect(idArticle, articlePerfect) == 0) {
            throw new ContentNotExistException("设置优选文章失败!");
        }
        return true;
    }

    @Override
    public List<ArticleDTO> findAnnouncements() {
        List<ArticleDTO> list = articleMapper.selectAnnouncements();
        list.forEach(articleDTO -> genArticle(articleDTO, 0));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateStatus(Long idArticle, String articleStatus, String remarks) {
        if (articleMapper.updateStatus(idArticle, articleStatus) == 0) {
            throw new ContentNotExistException("设置文章状态失败!");
        }
        Article article = articleMapper.selectByPrimaryKey(idArticle);
        String message = "你的文章《"+article.getArticleTitle()+"》";
        if ("1".equals(articleStatus)) {
            message += "已下架, 下架原因: " + remarks;
        } else {
            message += "已上架!";
        }
        applicationEventPublisher.publishEvent(new ArticleStatusEvent(idArticle, article.getArticleAuthorId(), message));
        return true;
    }

    private ArticleDTO genArticle(ArticleDTO article, Integer type) {
        Integer articleList = 0;
        Integer articleView = 1;
        Integer articleEdit = 2;
        Author author = genAuthor(article);
        article.setArticleAuthor(author);

        List<Interviewer> interviewers = genInterviewer(article);
        List<ChatRecordsDto> chatRecordsDtos = genChatRecords(article);
        article.setChatRecordsList(chatRecordsDtos);
        article.setInterviewerList(interviewers);

        article.setTimeAgo(Utils.getTimeAgo(article.getUpdatedTime()));
        List<ArticleTagDTO> tags = articleMapper.selectTags(article.getIdArticle());
        article.setTags(tags);
        if (!type.equals(articleList)) {
            ArticleContent articleContent = articleMapper.selectArticleContent(article.getIdArticle());
            if (type.equals(articleView)) {
                article.setArticleContent(XssUtils.filterHtmlCode(articleContent.getArticleContentHtml()));
                // 获取所属作品集列表数据
                List<PortfolioArticleDTO> portfolioArticleDTOList = articleMapper.selectPortfolioArticles(article.getIdArticle());
                portfolioArticleDTOList.forEach(this::genPortfolioArticles);
                article.setPortfolios(portfolioArticleDTOList);
                // 查询作者是否开通钱包账号
                article.setCanSponsor(Objects.nonNull(bankAccountService.findBankAccountByIdUser(article.getArticleAuthorId())));
            } else if (type.equals(articleEdit)) {
                article.setArticleContent(articleContent.getArticleContent());
            } else {
                article.setArticleContent(XssUtils.filterHtmlCode(articleContent.getArticleContentHtml()));
            }
        }
        return article;
    }

    private PortfolioArticleDTO genPortfolioArticles(PortfolioArticleDTO portfolioArticleDTO) {
        List<ArticleDTO> articles = articleMapper.selectPortfolioArticlesByIdPortfolioAndSortNo(portfolioArticleDTO.getIdPortfolio(), portfolioArticleDTO.getSortNo());
        portfolioArticleDTO.setArticles(articles);
        return portfolioArticleDTO;
    }

    private Author genAuthor(ArticleDTO article) {
        Author author = new Author();
        User user = userService.findById(String.valueOf(article.getArticleAuthorId()));
        author.setUserNickname(article.getArticleAuthorName());
        author.setUserAvatarURL(article.getArticleAuthorAvatarUrl());
        author.setIdUser(article.getArticleAuthorId());
        author.setUserAccount(user.getAccount());
        return author;
    }

    private List<ChatRecordsDto> genChatRecords(ArticleDTO articleDTO){
        // 从ShareReferenceMapper获取文章关联的聊天记录ID
        List<ShareReference> chatReferences = shareReferenceMapper.selectShareReferencesByArticleId(articleDTO.getIdArticle())
                .stream()
                .filter(ref -> ref.getType() != null && ref.getType() == 0 && ref.getChatId() != null)
                .collect(Collectors.toList());
        
        if (chatReferences.isEmpty()) {
            return new ArrayList<>();
        }

        return chatReferences.stream().map(reference -> {
            Long chatId = reference.getChatId();
            if (chatId == null) {
                return null;
            }
            
            // 通过chatId查询完整的聊天记录
            ChatRecords chatRecord = chatMapper.selectById(chatId);
            if (chatRecord == null) {
                return null;
            }
            
            // 创建并填充ChatRecordsDto
            ChatRecordsDto fullDto = new ChatRecordsDto();
            // 复制基本聊天记录属性
            fullDto.setChatId(chatRecord.getChatId());
            fullDto.setUserId(chatRecord.getUserId());
            fullDto.setInterviewerId(chatRecord.getInterviewerId());
            fullDto.setCreatedAt(chatRecord.getCreatedAt());
            fullDto.setUpdatedAt(chatRecord.getUpdatedAt());
            fullDto.setTopic(chatRecord.getTopic());
            
            // 获取面试官信息
            if (chatRecord.getInterviewerId() != null) {
                Interviewer interviewer = interviewerService.findInterviewerById(chatRecord.getInterviewerId());
                fullDto.setInterviewer(interviewer);
            }
            
            // 获取分支信息
            List<Branch> branches = branchRepository.findByChatId(chatId);
            fullDto.setBranchList(branches);
            
            return fullDto;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 包装面试官列表，根据ID获取完整面试官信息
     * @param articleDTO 文章DTO
     * @return 包装后的面试官列表
     */
    private List<Interviewer> genInterviewer(ArticleDTO articleDTO) {
        // 从ShareReferenceMapper获取文章关联的面试官ID
        List<ShareReference> interviewerReferences = shareReferenceMapper.selectShareReferencesByArticleId(articleDTO.getIdArticle())
                .stream()
                .filter(ref -> ref.getType() != null && ref.getType() == 1 && ref.getInterviewerId() != null)
                .collect(Collectors.toList());
        
        if (interviewerReferences.isEmpty()) {
            return new ArrayList<>();
        }
        
        return interviewerReferences.stream().map(reference -> {
            String interviewerId = reference.getInterviewerId();
            if (interviewerId == null || interviewerId.isEmpty()) {
                return null;
            }
            
            // 通过面试官ID查询完整的面试官信息
            Interviewer fullInterviewer = interviewerService.findInterviewerById(interviewerId);
            if (fullInterviewer == null) {
                return null;
            }
            
            return fullInterviewer;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private String checkTags(String articleTags) {
        // 判断文章是否有标签
        if (StringUtils.isBlank(articleTags)) {
            return "";
        }
        // 判断是否存在系统配置的保留标签词
        Condition condition = new Condition(Tag.class);
        condition.createCriteria().andEqualTo("tagReservation", "1");
        List<Tag> tags = tagService.findByCondition(condition);
        if (tags.isEmpty()) {
            return "";
        } else {
            String[] articleTagArr = articleTags.split(",");
            for (Tag tag : tags) {
                if (StringUtils.isBlank(tag.getTagTitle())) {
                    continue;
                }

                for (String articleTag : articleTagArr) {
                    if (StringUtils.isBlank(articleTag)) {
                        continue;
                    }
                    if (articleTag.equals(tag.getTagTitle())) {
                        return tag.getTagTitle();
                    }
                }
            }
        }

        return "";
    }
}
