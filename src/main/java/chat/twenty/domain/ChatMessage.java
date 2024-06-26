package chat.twenty.domain;

import chat.twenty.enums.ChatMessageType;
import chat.twenty.enums.UserType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter @Setter @ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity  @Table(name = "chat_message")
@Builder // mapStruct 에서 사용중
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id") private Long id;        // auto_increment

    private Long roomId;
    private Long userId;

    @Enumerated(EnumType.STRING)
    private ChatMessageType type;       // DB DEFAULT NONE

    private String username;    // 유저명
    private String content;
    private LocalDateTime createdAt;  // DB DEFAULT CURRENT_TIMESTAMP
    private boolean gptChat;      // Gpt 와의 대화인지 여부, DB DEFAULT false
    private String gptUuid;    // GPT activate 시의 세션구분. UUID(8)

    /**
     * 일반채팅 생성자
     */
    public ChatMessage(Long roomId, Long userId, ChatMessageType type, String content) {
        this.roomId = roomId;
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.createdAt = LocalDateTime.now().withNano(0);
    }

    /**
     * GPT 옵션 활성화 채팅 생성자
     */
    public ChatMessage(Long roomId, Long userId, ChatMessageType type, String username, String content, boolean isGptChat, String gptUuid) {
        this.roomId = roomId;
        this.userId = userId;
        this.username = username;
        this.type = type;
        this.content = content;
        this.gptChat = isGptChat;
        this.gptUuid = gptUuid;
        this.createdAt = LocalDateTime.now().withNano(0);
    }

    /**
     * GPT 질문 생성시, role:system 을 만들기 위해 사용하는 메서드, DB 영향 X
     */
    public void setGptSystemRole() {
        this.userId = UserType.SYSTEM.id;
        this.username = UserType.SYSTEM.username;
    }

    /**
     * GPT 질문 생성시, prompt 작성을 위해 사용되는 메서드, DB 영향 X
     */
    public void setGptPrompt(String prompt) {
        this.content = prompt;
    }

    /*
    ddl
    @Column(columnDefinition = "varchar(30)")
    @Enumerated(EnumType.STRING) private ChatMessageType type;       // DB DEFAULT NONE


    @Column(columnDefinition = "varchar(16)") private String username;    // 유저명
    @Column(columnDefinition = "varchar(100)") private String content;
    @Column(columnDefinition = "TIMESTAMP") private LocalDateTime createdAt;  // DB DEFAULT CURRENT_TIMESTAMP
    @Column(columnDefinition = "TINYINT(1)") private boolean gptChat;      // Gpt 와의 대화인지 여부, DB DEFAULT false
    @Column(columnDefinition = "varchar(8)") private String gptUuid;    // GPT activate 시의 세션구분. UUID(8)
     */

}
