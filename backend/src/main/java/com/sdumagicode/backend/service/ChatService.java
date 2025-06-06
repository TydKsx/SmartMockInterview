package com.sdumagicode.backend.service;

import com.alibaba.dashscope.app.ApplicationOutput;
import com.alibaba.dashscope.app.ApplicationResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sdumagicode.backend.core.result.GlobalResult;
import com.sdumagicode.backend.core.service.Service;
import com.sdumagicode.backend.dto.chat.ChatOutput;
import com.sdumagicode.backend.dto.chat.MessageFileDto;
import com.sdumagicode.backend.dto.chat.MessageLocalDto;
import com.sdumagicode.backend.entity.CodeSubmission;
import com.sdumagicode.backend.entity.chat.*;
import com.sdumagicode.backend.util.chatUtil.ChatUtil;
import io.reactivex.Flowable;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface ChatService {

        public List<ChatRecords> getChatRecords(ChatRecords chatRecords);

        public ChatRecords saveChatRecords(ChatRecords chatRecords);

        public boolean deleteChatRecords(ChatRecords chatRecords);

        public List<Branch> getAllBranches(ChatRecords chatRecords);

        public Flux<ChatOutput> sendMessageAndGetFlux(List<MessageLocal> messageList, String Prompt,
                        ChatUtil.AppType appType);

        public Flux<ChatOutput> sendMessageToInterviewerAndGetFlux(List<MessageLocal> messageList,
                        Interviewer interviewer);

        void sendMessageToInterviewer(
                        List<MessageLocal> messages,
                        Interviewer interviewer,
                        Long userId,
                        String messageId,
                        Consumer<ChatOutput> outputConsumer);

        void sendMessageToCoder(
                        CodeSubmission codeSubmission,
                        Long userId,
                        Consumer<ChatOutput> outputConsumer);

        public MessageFileDto convertMessageFile(MultipartFile multipartFile) throws IOException;

        public boolean saveBranches(List<Branch> branchList);

        public List<MessageLocal> convertMessageListDto(List<MessageLocalDto> messageLocalDtoList);

        public ValuationRecord getValuationRecord(Long chatId);

        /**
         * 更新聊天话题
         * 
         * @param chatId   聊天ID
         * @param newTopic 新话题名称
         * @return 是否更新成功
         */
        boolean updateChatTopic(Long chatId, String newTopic);

        public ChatRecords deepCopy(Long chatId,Long userId);

        /**
         * 批量删除聊天记录
         * @param chatIds 聊天记录ID列表
         * @return 是否删除成功
         */
        public boolean batchDeleteChatRecords(List<Long> chatIds);

}
