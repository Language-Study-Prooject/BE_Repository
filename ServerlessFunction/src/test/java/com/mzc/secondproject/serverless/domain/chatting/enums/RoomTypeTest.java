package com.mzc.secondproject.serverless.domain.chatting.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomTypeTest {
	@Test
	void testFromString() {
		assertEquals(RoomType.CHAT, RoomType.fromString("chat"));
		assertEquals(RoomType.CHAT, RoomType.fromString("CHAT"));
		assertEquals(RoomType.GAME, RoomType.fromString("game"));
		assertEquals(RoomType.GAME, RoomType.fromString("GAME"));
		assertEquals(RoomType.CHAT, RoomType.fromString(null));
		assertEquals(RoomType.CHAT, RoomType.fromString("invalid"));
	}
	
	@Test
	void testIsValid() {
		assertTrue(RoomType.isValid("CHAT"));
		assertTrue(RoomType.isValid("chat"));
		assertTrue(RoomType.isValid("GAME"));
		assertTrue(RoomType.isValid("game"));
		assertFalse(RoomType.isValid(null));
		assertFalse(RoomType.isValid("invalid"));
	}
	
	@Test
	void testGetCode() {
		assertEquals("chat", RoomType.CHAT.getCode());
		assertEquals("game", RoomType.GAME.getCode());
	}
	
	@Test
	void testGetDisplayName() {
		assertEquals("채팅방", RoomType.CHAT.getDisplayName());
		assertEquals("게임방", RoomType.GAME.getDisplayName());
	}
}
