package solobob.solobobmate.chat;

import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import solobob.solobobmate.auth.config.SecurityUtil;
import solobob.solobobmate.auth.jwt.TokenProvider;
import solobob.solobobmate.chat.chatDto.ChatMessageDto;
import solobob.solobobmate.chat.chatDto.ChatMessageResponseDto;
import solobob.solobobmate.chat.chatDto.LocationDto;
import solobob.solobobmate.controller.exception.ErrorCode;
import solobob.solobobmate.controller.exception.SoloBobException;
import solobob.solobobmate.domain.Chat;
import solobob.solobobmate.domain.Member;
import solobob.solobobmate.repository.MemberRepository;
import solobob.solobobmate.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
@Slf4j
public class ChatController {

    private final TokenProvider tokenProvider;
    private final RedisPublisher redisPublisher;
    private final RedisSubscriber redisSubscriber;
    private final RedisLocationSubscriber redisLocationSubscriber;
    private final ChatService chatService;
    private final MemberRepository memberRepository;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    public Member getMember() {
        Member member = memberRepository.findByLoginId(SecurityUtil.getCurrentMemberId()).orElseThrow(
                () -> new SoloBobException(ErrorCode.NOT_FOUND_MEMBER)
        );
        return member;
    }


    @MessageMapping("/party/message")  // /pub/party/message
    public void messageChat(@RequestBody ChatMessageDto chatMessageDto, SimpMessageHeaderAccessor accessor) {
        log.info("메시지: {}", chatMessageDto.getMessage());

        String jwtToken = accessor.getFirstNativeHeader("Authorization").substring(7);
        if (tokenProvider.validateToken(jwtToken)) {
            Authentication authentication = tokenProvider.getAuthentication(jwtToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        Member member = getMember();

        Chat chat = chatService.create(member, chatMessageDto);

        // redis subscriber 설정
        redisMessageListenerContainer.addMessageListener(redisSubscriber, new ChannelTopic("/sub/chat/" + chatMessageDto.getRoomId()));
        // 메시지 발행
        redisPublisher.publish(new ChannelTopic("/sub/chat/" + chatMessageDto.getRoomId()), new ChatMessageResponseDto(chat));
    }


    @MessageMapping("/party/position")
    public void chaseLocation(@RequestBody LocationDto locationDto, SimpMessageHeaderAccessor accessor){
        log.info("위도: {}", locationDto.getLatitude());
        log.info("경도: {}", locationDto.getLongitude());

        String jwtToken = accessor.getFirstNativeHeader("Authorization").substring(7);
        if (tokenProvider.validateToken(jwtToken)) {
            Authentication authentication = tokenProvider.getAuthentication(jwtToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // redis subscriber 설정
        redisMessageListenerContainer.addMessageListener(redisLocationSubscriber, new ChannelTopic("/sub/position/" + locationDto.getRoomId()));
        // 메시지 발행
        redisPublisher.publish(new ChannelTopic("/sub/position/" + locationDto.getRoomId()), locationDto);

        // Stomp 사용 코드
//        log.info("locationDto room id: {}", locationDto.getRoomId());
//        template.convertAndSend("/sub/position/"+locationDto.getRoomId(), locationDto);
    }


}
