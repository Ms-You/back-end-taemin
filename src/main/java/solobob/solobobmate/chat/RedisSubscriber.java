package solobob.solobobmate.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import solobob.solobobmate.chat.chatDto.ChatMessageResponseDto;
import solobob.solobobmate.controller.exception.ErrorCode;
import solobob.solobobmate.controller.exception.SoloBobException;
import solobob.solobobmate.domain.Chat;
import solobob.solobobmate.repository.ChatRepository;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RedisTemplate redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatRepository chatRepository;

    /**
     * Redis에서 메시지가 발행(publish)되면 대기하고 있던 onMessage가 해당 메시지를 받아 처리한다.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 발행된 데이터를 받아 deserialize
            String publishMessage = (String) redisTemplate.getStringSerializer().deserialize(message.getBody());
            // ChatMessage 객체로 매핑
            ChatMessageResponseDto roomMessage = objectMapper.readValue(publishMessage, ChatMessageResponseDto.class);

            Chat chat = chatRepository.findById(roomMessage.getChatId()).orElseThrow(
                    () -> new SoloBobException(ErrorCode.NOT_FOUND_CHATROOM)
            );

            // 해당 경로의 구독자에게 채팅 메시지 Send
            messagingTemplate.convertAndSend("/sub/chat/" + chat.getChatRoom().getId(), roomMessage);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
