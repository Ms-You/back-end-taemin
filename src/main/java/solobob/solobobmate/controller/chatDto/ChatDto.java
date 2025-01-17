package solobob.solobobmate.controller.chatDto;

import lombok.Data;
import solobob.solobobmate.domain.Chat;

import java.time.LocalDateTime;

@Data
public class ChatDto {

    private String sender;

    private String message;

    private LocalDateTime sendTime;

    public ChatDto(Chat chat) {
        this.sender = chat.getSender();
        this.message = chat.getMessage();
        this.sendTime = chat.getCreateAt();
    }
}
