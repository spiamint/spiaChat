package chat.twenty.enums;

import lombok.Getter;

/**
 * 사전 정의 및 관리될 유저 타입
 */
@Getter
public enum UserType {

    ADMIN(1L, "admin"),             // 관리자
    GPT(3L, "GPT"),                 // User 로써의 GPT 패스워드 gptgpt
    SYSTEM(4L, "System")            // GPT 질의를 위한 시스템유저
    ;

    public final Long id;          // DB 의 id
    public final String username;

    // 컴파일러에 의해서만 호출
    UserType(Long id, String username) {
        this.id = id;
        this.username = username;
    }



}
