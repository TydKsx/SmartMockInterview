package com.sdumagicode.backend.lucene.service.impl;

import com.sdumagicode.backend.dto.UserDTO;
import com.sdumagicode.backend.lucene.lucene.IKAnalyzer;
import com.sdumagicode.backend.lucene.lucene.UserBeanIndex;
import com.sdumagicode.backend.lucene.mapper.UserLuceneMapper;
import com.sdumagicode.backend.lucene.model.UserLucene;
import com.sdumagicode.backend.lucene.service.UserLuceneService;
import com.sdumagicode.backend.lucene.util.LucenePath;
import com.sdumagicode.backend.lucene.util.SearchUtil;
import com.sdumagicode.backend.lucene.util.UserIndexUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UserServiceImpl
 *
 * @author suwen
 * @date 2021/3/6 10:29
 */
@Slf4j
@Service
public class UserLuceneServiceImpl implements UserLuceneService {

    @Resource
    private UserLuceneMapper userLuceneMapper;


    /**
     * 将文章的数据解析为一个个关键字词存储到索引文件中
     *
     * @param list
     */
    @Override
    public void writeUser(List<UserLucene> list) {
        try {
            int totalCount = list.size();
            int perThreadCount = 3000;
            int threadCount = totalCount / perThreadCount + (totalCount % perThreadCount == 0 ? 0 : 1);
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch countDownLatch1 = new CountDownLatch(1);
            CountDownLatch countDownLatch2 = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                int start = i * perThreadCount;
                int end = Math.min((i + 1) * perThreadCount, totalCount);
                List<UserLucene> subList = list.subList(start, end);
                Runnable runnable =
                        new UserBeanIndex(LucenePath.USER_PATH, i, countDownLatch1, countDownLatch2, subList);
                // 子线程交给线程池管理
                pool.execute(runnable);
            }
            countDownLatch1.countDown();
            log.info("开始创建索引");
            // 等待所有线程都完成
            countDownLatch2.await();
            // 线程全部完成工作
            log.info("所有线程都创建索引完毕");
            // 释放线程池资源
            pool.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void writeUser(String id) {
        writeUser(userLuceneMapper.getById(id));
    }

    @Override
    public void writeUser(UserLucene UserLucene) {
        UserIndexUtil.addIndex(UserLucene);
    }

    @Override
    public void updateUser(String id) {
        UserIndexUtil.updateIndex(userLuceneMapper.getById(id));
    }

    @Override
    public void deleteUser(String id) {
        UserIndexUtil.deleteIndex(id);
    }

    @Override
    public List<UserLucene> searchUser(String value) {
        List<UserLucene> resList = new ArrayList<>();
        ExecutorService service = Executors.newCachedThreadPool();
        // 定义分词器
        Analyzer analyzer = new IKAnalyzer();
        try {
            IndexSearcher searcher = SearchUtil.getIndexSearcherByParentPath(LucenePath.USER_PATH, service);
            String[] fields = {"nickname", "signature"};
            // 构造Query对象
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);

            BufferedReader in =
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = value != null ? value : in.readLine();
            Query query = parser.parse(line);
            // 最终被分词后添加的前缀和后缀处理器，默认是粗体<B></B>
            SimpleHTMLFormatter htmlFormatter =
                    new SimpleHTMLFormatter("<font color=" + "\"" + "red" + "\"" + ">", "</font>");
            // 高亮搜索的词添加到高亮处理器中
            Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(query));

            // 获取搜索的结果，指定返回document返回的个数
            // TODO 默认搜索结果为显示第一页，1000 条，可以优化
            TopDocs results = SearchUtil.getScoreDocsByPerPage(1, 100, searcher, query);
            ScoreDoc[] hits = results.scoreDocs;

            // 遍历，输出
            for (ScoreDoc hit : hits) {
                int id = hit.doc;
                float score = hit.score;
                Document hitDoc = searcher.doc(hit.doc);
                // 获取到 signature
                String signature = hitDoc.get("signature");
                // 将查询的词和搜索词匹配，匹配到添加前缀和后缀
                TokenStream tokenStream = TokenSources.getTokenStream("signature", searcher.getIndexReader().getTermVectors(id), signature, analyzer, -1);

                // 传入的第二个参数是查询的值
                TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, signature, false, 10);
                StringBuilder baikeValue = new StringBuilder();
                for (TextFragment textFragment : frag) {
                    if ((textFragment != null) && (textFragment.getScore() > 0)) {
                        //  if ((frag[j] != null)) {
                        // 获取 summary 的值
                        baikeValue.append(textFragment.toString());
                    }
                }
                // 获取到 nickname
                String nickname = hitDoc.get("nickname");
                TokenStream titleTokenStream = TokenSources.getTokenStream("nickname", searcher.getIndexReader().getTermVectors(id), nickname, analyzer, -1);
                TextFragment[] titleFrag =
                        highlighter.getBestTextFragments(titleTokenStream, nickname, false, 10);
                StringBuilder titleValue = new StringBuilder();
                for (int j = 0; j < titleFrag.length; j++) {
                    if ((frag[j] != null)) {
                        titleValue.append(titleFrag[j].toString());
                    }
                }
                resList.add(
                        UserLucene.builder()
                                .idUser(Long.valueOf(hitDoc.get("id")))
                                .nickname(titleValue.toString())
                                .signature(baikeValue.toString())
                                .score(String.valueOf(score))
                                .build());
            }
        } catch (IOException | ParseException | InvalidTokenOffsetsException e) {
            log.info(e.getMessage());
            e.printStackTrace();
        } finally {
            service.shutdownNow();
        }
        return resList;
    }

    @Override
    public List<UserLucene> getAllUserLucene() {
        return userLuceneMapper.getAllUserLucene();
    }

    @Override
    public List<UserDTO> getUsersByIds(Long[] ids) {
        return userLuceneMapper.getUsersByIds(ids);
    }
}
