package chat.twenty.service.gpt;

import chat.twenty.domain.ChatMessage;
import chat.twenty.domain.ChatRoom;
import chat.twenty.domain.RoomMember;
import chat.twenty.dto.ChatMessageDto;
import chat.twenty.dto.TwentyMessageDto;
import chat.twenty.enums.ChatMessageType;
import chat.twenty.enums.TwentyGameSubject;
import chat.twenty.enums.UserType;
import chat.twenty.service.lower.ChatMessageService;
import chat.twenty.service.lower.ChatRoomService;
import chat.twenty.service.lower.RoomMemberService;
import io.github.flashvayne.chatgpt.dto.chat.MultiChatMessage;
import io.github.flashvayne.chatgpt.exception.ChatgptException;
import io.github.flashvayne.chatgpt.service.ChatgptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ConnectTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import javax.persistence.EntityManager;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GPT 관련 처리를 하는 서비스, 라이브러리의 DefaultGptService 를 사용한다.
 * 라이브러리의 DefaultGptService 를 의존하는 유일한 객체일것.
 * <p>
 * 다른 서비스를 참조하는 상위서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomGptService {
    private final ChatgptService defaultGptService; // 라이브러리로 부터 DefaultChatGptService 를 주입받아 사용
    private final RoomMemberService memberService;
    private final ChatRoomService roomService;
    private final ChatMessageService chatMessageService;
    private final EntityManager em;

    /**
     * Member 와 Chatroom 에서 gpt 활성화, 활성화된 gpt 의 UUID 반환
     */
    public String activateGpt(Long roomId, Long userId) {
        String gptUuid = java.util.UUID.randomUUID().toString().substring(0, 8);

        ChatRoom room = roomService.findById(roomId);
        room.setGptActivated(true);
        room.setGptUuid(gptUuid);

        RoomMember member = memberService.findByRoomIdAndUserId(roomId, userId); // 엔티티의 데이터변경을 위해 조회
        member.setGptOwner(true);
        return gptUuid;
    }

    /**
     * Member 와 Chatroom 에서 gpt 비활성화, 성공여부 반환
     */
    public void deActivateGpt(Long roomId, Long userId) {
        RoomMember member = memberService.findByRoomIdAndUserId(roomId, userId);
        log.info("deActivateGpt, isGptOwner = {}, roomId = {}, userId = {}", member.isGptOwner(), roomId, userId);
        if (member.isGptOwner()) {
            member.setGptOwner(false);
            ChatRoom room = roomService.findById(roomId);
            room.setGptActivated(false);
            room.setGptUuid(null);
        }
    }

    /**
     * RoomMember, MessageType 을 이용하여 GPT 답변 메시지를 생성후 리턴
     */
    public ChatMessageDto sendGptChatRequest(Long roomId) {
        // gpt 식별자 획득
        String gptUuid = roomService.findById(roomId).getGptUuid();
        // 현재 gpt 와의 채팅목록 조회
        List<ChatMessage> chatMessageList = chatMessageService.findCurrentGptQueue(roomId, gptUuid);

        // 첫번째 메시지 프롬프트로 변경
        ChatMessage firstMessage = chatMessageList.get(0);
        setSystemPrompt(firstMessage, ChatMessageType.ACTIVATE_GPT, null, null);

        // gpt 요청 리스트 작성
        List<MultiChatMessage> gptRequestMessageList = makeGptRequestList(chatMessageList);

        // gpt 요청 후 응답
        String gptResponse = askMultiChatGpt(gptUuid, gptRequestMessageList);

        // GPT 답변으로 Message 객체 생성후 리턴
        return ChatMessageDto.createGptAnswerMessage(roomId, gptUuid, gptResponse);
    }

    /**
     * 스무고개 게임 진행중 GPT 에게 질문을 보냄.
     */
    public TwentyMessageDto sendGptTwentyRequest(Long roomId, Long userId) {
        ChatRoom room = roomService.findById(roomId);
        String gptUuid = room.getGptUuid(); // gpt 식별자 획득
        TwentyGameSubject subject = room.getSubject(); // 게임 주제 획득

        // roomId 와 gptUuid 를 기반으로, 현재 gpt 와의 채팅목록 조회
        List<ChatMessage> twentyMessageList = chatMessageService.findCurrentGptQueue(roomId, gptUuid);

        // 첫번째 메시지 프롬프트로 변경
        ChatMessage firstMessage = twentyMessageList.get(0);
        String twentyAnswer = roomService.findTwentyAnswer(roomId); // nullable
        setSystemPrompt(firstMessage, firstMessage.getType(), subject, twentyAnswer);

        // gpt 요청리스트 작성
        List<MultiChatMessage> gptRequestList = makeGptRequestList(twentyMessageList);

        // gpt 요청 후 응답
        String gptResponse = askMultiChatGpt(gptUuid, gptRequestList);

        // GPT 답변으로 TwentyMessage 객체 생성후 리턴
        return TwentyMessageDto.createGptAnswerMessage(roomId, gptUuid, gptResponse);
    }


    // protected =====================================================================================

    /**
     * GPT 요청 메시지 리스트의 system 프롬프트 작성
     *
     * @param firstMessage     : content 가 프롬프트로 replace 될 BaseMessage.
     * @param firstMessageType : TWENTY_GAME_START, ACTIVATE_GPT
     */
    protected void setSystemPrompt(ChatMessage firstMessage, ChatMessageType firstMessageType, TwentyGameSubject subject, String answer) {

        firstMessage.setGptSystemRole();

        switch (firstMessageType) {
            case ACTIVATE_GPT:
                firstMessage.setGptPrompt(GptPrompt.CHAT_PROMPT.prompt);
                break;
            case TWENTY_GAME_START:
                em.detach(firstMessage); // 영속성 제거 (DB 변경X, GPT 내용보내기 전용)
                // subject not null
                if (subject == TwentyGameSubject.CUSTOM)
                    // 아직 임시 구현, 사용자지정 주제
                    firstMessage.setGptPrompt(GptPrompt.TWENTY_LEGACY_PROMPT
                            .setLegacyTwentyPrompt(roomService.findById(firstMessage.getRoomId()).getCustomSubject()));
                else
                    firstMessage.setGptPrompt(GptPrompt.TWENTY_PROMPT
                            .setTwentyPrompt(subject.getSubjectName(), answer));
                break;
        }
    }

    /**
     * GPT 에 보낼 메시지 리스트를 라이브러리 스펙 List<MultiChatMessage>에 맞게 생성
     */
    protected List<MultiChatMessage> makeGptRequestList(List<ChatMessage> messageList) {
        // MultiChatMessage 리스트 생성
        List<MultiChatMessage> requestMessageList = new ArrayList<>();

        messageList.stream().forEachOrdered(message ->
                requestMessageList.add(new MultiChatMessage(
                        // role = "system" or "assistant" or "user"
                        determineRole(message.getUserId()),
                        // content = "username: content" or "content" when assistant
                        " " + message.getContent())));

        // 전체 리스트 로그출력
        log.info("raw gptRequestList in makeGptMessageList()\n{}", requestMessageList.stream()
                .map(MultiChatMessage::getContent)
                .collect(Collectors.joining("\n")));

        return requestMessageList;
    }

    /**
     * message.userId 에 따라 gpt 에게 보낼 role 을 결정한다.
     * "system", "assistant", "user" 중 하나를 반환
     */
    protected String determineRole(long id) {
        if (id == UserType.GPT.getId()) {
            return "assistant";
        } else if (id == UserType.SYSTEM.getId()) {
            return "system";
        } else {
            return "user";
        }
    }

    /**
     * 라이브러리를 사용하여, GPT 에게 질문 요청
     * @param gptUuid               : gpt 식별자 (나중에 roomId 도 같이 넣을지 고려)
     * @param gptRequestMessageList : 요청 MultiChatMessage 리스트 (라이브러리 스펙)
     * @return
     */
    protected String askMultiChatGpt(String gptUuid, List<MultiChatMessage> gptRequestMessageList) {
//        log.info("askMultiChatGpt() gptRequestMessageList = {}", gptRequestMessageList);
        String gptResponse = ""; // GPT 답변

        try {
            gptResponse = defaultGptService.multiChat(gptRequestMessageList);

        } catch (HttpServerErrorException.ServiceUnavailable e) {
            // GPT response Error
            log.info("askMultiChatGpt ServiceUnavailable. e = {},\n message = {}, gptUuid = {}", e, e.getMessage(), gptUuid);
            gptResponse = "GPT 서버가 현재 과부하상태(overloaded) 입니다. 잠시후 다시 시도해주세요";
        } catch (ChatgptException e) {
            log.info("askMultiChatGpt ChatgptException e = {},\n message = {}, gptUuid = {}", e, e.getMessage(), gptUuid);
            gptResponse = "GPT 응답에 문제가 발생했습니다. 다시 시도해주세요"; // response statusCode != HttpStatus.OK
        } catch (ResourceAccessException e) {
            log.info("askMultiChatGpt ResourceAccessException e = {},\n message = {}, rootCause ={} gptUuid = {}", e, e.getMessage(), e.getRootCause(), gptUuid);
            if (e.getRootCause() instanceof SocketTimeoutException || e.getRootCause() instanceof ConnectTimeoutException)
                gptResponse = "GPT 응답시간이 초과되었습니다. 다시 시도해주세요"; // RestTemplate timeout
             else
                gptResponse = "GPT 응답에 문제가 발생했습니다. 새로고침 해주세요.";

        } catch (Exception e) {
            log.info("askMultiChatGpt Exception. e = {},\n message = {}, gptUuid = {}, cause={}", e, e.getMessage(), gptUuid, e.getCause());
            gptResponse = "시스템 오류로 인해 GPT 응답에 문제가 발생했습니다. 새로고침 해주세요.";
        }

        log.info("askMultiChatGpt(), gptUuid = {}, gptResponse = {}", gptUuid, gptResponse);

        return gptResponse;
    }
}
