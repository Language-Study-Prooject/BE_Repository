package com.mzc.secondproject.serverless.domain.chatting.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomStatusTest {
	@Test
	void testFromString() {
		assertEquals(RoomStatus.WAITING, RoomStatus.fromString("waiting"));
		assertEquals(RoomStatus.WAITING, RoomStatus.fromString("WAITING"));
		assertEquals(RoomStatus.PLAYING, RoomStatus.fromString("playing"));
		assertEquals(RoomStatus.PLAYING, RoomStatus.fromString("PLAYING"));
		assertEquals(RoomStatus.FINISHED, RoomStatus.fromString("finished"));
		assertEquals(RoomStatus.FINISHED, RoomStatus.fromString("FINISHED"));
		assertEquals(RoomStatus.WAITING, RoomStatus.fromString(null));
		assertEquals(RoomStatus.WAITING, RoomStatus.fromString("invalid"));
	}
	
	@Test
	void testIsValid() {
		assertTrue(RoomStatus.isValid("WAITING"));
		assertTrue(RoomStatus.isValid("waiting"));
		assertTrue(RoomStatus.isValid("PLAYING"));
		assertTrue(RoomStatus.isValid("playing"));
		assertTrue(RoomStatus.isValid("FINISHED"));
		assertTrue(RoomStatus.isValid("finished"));
		assertFalse(RoomStatus.isValid(null));
		assertFalse(RoomStatus.isValid("invalid"));
	}
	
	@Test
	void testGetCode() {
		assertEquals("waiting", RoomStatus.WAITING.getCode());
		assertEquals("playing", RoomStatus.PLAYING.getCode());
		assertEquals("finished", RoomStatus.FINISHED.getCode());
	}
	
	@Test
	void testGetDisplayName() {
		assertEquals("대기 중", RoomStatus.WAITING.getDisplayName());
		assertEquals("게임 중", RoomStatus.PLAYING.getDisplayName());
		assertEquals("종료됨", RoomStatus.FINISHED.getDisplayName());
	}
}
