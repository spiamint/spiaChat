package chat.twenty.service;

import chat.twenty.dto.TwentyMessageDto;
import chat.twenty.enums.ChatMessageType;
import chat.twenty.exception.TwentyGameOrderNotValidException;
import chat.twenty.service.gpt.CustomGptService;
import chat.twenty.service.lower.ChatRoomService;
import chat.twenty.service.lower.RoomMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 스무고개 기능 관련  상위 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TwentyGameService {

    private final RoomMemberService memberService;
    private final ChatRoomService roomService;
    private final CustomGptService gptService;

    public boolean readyGame(Long roomId, Long userId) {
        return memberService.twentyReady(roomId, userId);
    }

    public boolean unreadyGame(Long roomId, Long userId) {
        return memberService.twentyUnready(roomId, userId);
    }

    // TODO try - catch 로 proceedGame 처럼.
    public boolean validateGameStart(Long roomId) {
        int memberCount = memberService.countMemberByRoomId(roomId);
        int readyMemberCount = memberService.countTwentyReadyMemberByRoomId(roomId);
        log.info("validateGameStart() memberCount = {} readyMemberCount = {}", memberCount, readyMemberCount);
        return memberCount == readyMemberCount;
    }

    /**
     * 시작 검증후, 게임 시작
     * TWENTY_GAME_START
     */
    public TwentyMessageDto confirmGameStart(TwentyMessageDto twentyMessageDto) {
        // 순서결정용 배열 생성
        Integer[] orderArray = makeOrderArray(twentyMessageDto.getRoomId());
        // GPT 활성화 처리 및 uuid 획득
        String gptUuid = gptService.activateGpt(twentyMessageDto.getRoomId(), twentyMessageDto.getUserId());

        twentyMessageDto.setOrderArray(orderArray);
        twentyMessageDto.setGptUuid(gptUuid);
        return twentyMessageDto;
    }

    /**
     * 스무고개 순서 배정용 배열 생성 및 반환
     * TWENTY_GAME_START
     */
    protected Integer[] makeOrderArray(Long roomId) {
        Integer[] orderArray = new Integer[memberService.countTwentyReadyMemberByRoomId(roomId)];
        for (int i = 0; i < orderArray.length; i++) {
            orderArray[i] = i;
        }

        List<Integer> orderList = Arrays.asList(orderArray);
        Collections.shuffle(orderList);

        orderArray = orderList.toArray(new Integer[orderList.size()]); // Object[] -> Integer[]
        return orderArray;
    }

    public TwentyMessageDto proceedStart(Long roomId) {
        // TWENTY_GAME_START 일때 currentOrder == -1, 기존 order 초기화 및 검증
        resetOrder(roomId);

        TwentyMessageDto gptRespMessage = gptService.sendGptTwentyRequest(roomId);
        gptRespMessage.setTwentyNext(0);

        return gptRespMessage;
    }

    /**
     * 스무고개 순서 초기화 후 다음순서(0) 을 반환
     */
    protected int resetOrder(long roomId) {
        roomService.resetTwentyOrder(roomId);
        return 0;
    }

    public TwentyMessageDto proceedGame(Long roomId, int currentOrder) {

        // 순서 검증
        validateOrder(roomId, currentOrder);

        // GPT 질의
        TwentyMessageDto gptRespMessage = gptService.sendGptTwentyRequest(roomId);

        // 순서 진행
        int nextOrder = proceedOrder(roomId, currentOrder);
        gptRespMessage.setTwentyNext(nextOrder);


        log.info("proceedGame() nextOrder = {}", nextOrder);
        return gptRespMessage;
    }

    protected boolean validateOrder(Long roomId, int currentOrder) {
        if (roomService.findById(roomId).getTwentyNext() == currentOrder) {
            log.info("validateOrder() currentOrder is valid, currentOrder = " + currentOrder + " roomId = " + roomId);
            return true;
        } else {
            throw new TwentyGameOrderNotValidException("TwentyGameService.validateOrder() currentOrder is not valid," +
                    " currentOrder = " + currentOrder + " roomId = " + roomId, currentOrder);
        }
    }

    /**
     * 스무고개 순서 진행 후 다음 순서를 반환
     */
    protected int proceedOrder(long roomId, int currentOrder) {
        int playerCount = memberService.countTwentyReadyMemberByRoomId(roomId);

        // 다음순서는 현재순서 + 1 이며, 이 순서가 전체유저 수 보다 크면 0으로 돌아감.
        int nextOrder = ++currentOrder < playerCount ? currentOrder : 0;

        roomService.updateNextTwentyOrder(roomId, nextOrder);
        return roomService.findById(roomId).getTwentyNext();
    }

    protected boolean validateAnswer(String gptResponse) {
        return gptResponse.contains("##");
    }

    public TwentyMessageDto proceedAnswer(Long roomId, String username, TwentyMessageDto gptRespMessage) {
        log.info("proceedGame() validate Answer true, roomId = {}, content = {}", roomId, gptRespMessage.getContent());
        finishGame(roomId);

        gptRespMessage.setType(ChatMessageType.TWENTY_GAME_END);
        gptRespMessage.setTwentyWinner(username);

        return gptRespMessage;
    }

    protected void finishGame(long roomId) {
        resetOrder(roomId);

        memberService.findIsTwentyReadyMemberByRoomId(roomId).forEach(member -> {
            memberService.twentyUnready(roomId, member.getUserId()); // 모든 유저 ready 해제
            roomService.updateGptActivated(roomId, false); // gptActivated 초기화
            memberService.updateGptUuid(roomId, member.getUserId(), null); // gptUuid 초기화
        });
    }
}
