package solobob.solobobmate.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import solobob.solobobmate.chat.chatDto.LocationDto;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisLocationSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RedisTemplate redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;


    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 발행된 데이터를 받아 deserialize
            String publishMessage = (String) redisTemplate.getStringSerializer().deserialize(message.getBody());
            // LocationDto 객체로 매핑
            LocationDto locationDto = objectMapper.readValue(publishMessage, LocationDto.class);

            // 해당 경로의 구독자에게 위치 정보 Send
            messagingTemplate.convertAndSend("/sub/position/" + locationDto.getRoomId(), locationDto);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
