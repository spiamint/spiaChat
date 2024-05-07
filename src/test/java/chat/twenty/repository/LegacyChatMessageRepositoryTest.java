package chat.twenty.repository;

import chat.twenty.domain.ChatMessage;
import chat.twenty.enums.ChatMessageType;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@Slf4j
@Transactional
class LegacyChatMessageRepositoryTest {

    @Autowired
    LegacyChatMessageRepository legacyChatMessageRepository;
    List<ChatMessage> testChatMessageList;

    @BeforeEach
    void init() {
        // roomId 1L 2L, userId 1L, 2L 은 사전에 DB 에 존재.
        testChatMessageList = List.of(
                new ChatMessage(1L, 1L, ChatMessageType.CHAT, "testMessage r1 u1"),
                new ChatMessage(1L, 2L, ChatMessageType.CHAT, "testMessage r1 u2"),
                new ChatMessage(2L, 1L, ChatMessageType.CHAT, "testMessage r2 u1"),
                new ChatMessage(2L, 2L, ChatMessageType.CHAT, "testMessage r2 u2")
        );
    }

    @Test
    void saveAndFindById() {
        testChatMessageList.forEach(message -> legacyChatMessageRepository.save(message));

        testChatMessageList.stream()
                .map(message -> legacyChatMessageRepository.save(message))
                // save() 로 리턴된 Stream<Message>
                .forEach(message -> {
                    Assertions.assertThat(legacyChatMessageRepository.findById(message.getId()))
                            .isEqualToIgnoringGivenFields(message, "createdAt");
                });
    }

    @Test
    void findAll() {
        testChatMessageList.forEach(message -> legacyChatMessageRepository.save(message));

        List<ChatMessage> findChatMessageList = legacyChatMessageRepository.findAll();

        Assertions.assertThat(findChatMessageList).usingElementComparatorIgnoringFields("createdAt").containsAll(testChatMessageList);
    }

    @Test
    void findByRoomId() {
        testChatMessageList.forEach(message -> legacyChatMessageRepository.save(message));

        List<ChatMessage> findChatMessageList = legacyChatMessageRepository.findByRoomId(1L);

        List<ChatMessage> room1LChatMessageList = testChatMessageList.stream().filter(message -> message.getRoomId().equals(1L)).collect(Collectors.toList());
        Assertions.assertThat(findChatMessageList).usingElementComparatorIgnoringFields("createdAt").containsAll(room1LChatMessageList);
    }

    @Test
    void findByRoomIdAndUserId() {
        testChatMessageList.forEach(message -> legacyChatMessageRepository.save(message));

        List<ChatMessage> findChatMessageList = legacyChatMessageRepository.findByRoomIdAndUserId(1L, 1L);

        List<ChatMessage> room1LUser1LChatMessageList = testChatMessageList.stream().filter(message -> message.getRoomId().equals(1L) && message.getUserId().equals(1L)).collect(Collectors.toList());
        Assertions.assertThat(findChatMessageList).usingElementComparatorIgnoringFields("createdAt").containsAll(room1LUser1LChatMessageList);
    }

    @Test
    void update() {
        testChatMessageList.forEach(message -> legacyChatMessageRepository.save(message));

        ChatMessage updateChatMessage = testChatMessageList.get(0);
        updateChatMessage.setContent("updateMessage");

        legacyChatMessageRepository.update(updateChatMessage.getId(), updateChatMessage);

        Assertions.assertThat(legacyChatMessageRepository.findById(updateChatMessage.getId()))
                .isEqualToIgnoringGivenFields(updateChatMessage, "createdAt");
    }

    @Test
    void deleteById() {
        testChatMessageList.forEach(message -> legacyChatMessageRepository.save(message));

        Long deleteMessageId = testChatMessageList.get(0).getId();
        legacyChatMessageRepository.deleteById(deleteMessageId);

        Assertions.assertThat(legacyChatMessageRepository.findById(deleteMessageId)).isNull();
    }
}