package com.mzc.secondproject.serverless.domain.chatting.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSettingsTest {
	@Test
	void testDefaultValues() {
		GameSettings settings = GameSettings.builder().build();
		assertEquals(5, settings.getMaxRounds());
		assertEquals(60, settings.getRoundTimeLimit());
		assertFalse(settings.getAutoDeleteOnEnd());
	}

	@Test
	void testCustomValues() {
		GameSettings settings = GameSettings.builder()
				.maxRounds(10)
				.roundTimeLimit(90)
				.autoDeleteOnEnd(true)
				.build();
		assertEquals(10, settings.getMaxRounds());
		assertEquals(90, settings.getRoundTimeLimit());
		assertTrue(settings.getAutoDeleteOnEnd());
	}

	@Test
	void testNoArgsConstructor() {
		GameSettings settings = new GameSettings();
		assertEquals(5, settings.getMaxRounds());
		assertEquals(60, settings.getRoundTimeLimit());
		assertFalse(settings.getAutoDeleteOnEnd());
	}

	@Test
	void testAllArgsConstructor() {
		GameSettings settings = new GameSettings(10, 90, true);
		assertEquals(10, settings.getMaxRounds());
		assertEquals(90, settings.getRoundTimeLimit());
		assertTrue(settings.getAutoDeleteOnEnd());
	}

	@Test
	void testSettersAndGetters() {
		GameSettings settings = new GameSettings();
		settings.setMaxRounds(8);
		settings.setRoundTimeLimit(120);
		settings.setAutoDeleteOnEnd(true);

		assertEquals(8, settings.getMaxRounds());
		assertEquals(120, settings.getRoundTimeLimit());
		assertTrue(settings.getAutoDeleteOnEnd());
	}
}
