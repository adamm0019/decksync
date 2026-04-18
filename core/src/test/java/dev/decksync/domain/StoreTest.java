package dev.decksync.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoreTest {

  @Test
  void steamTagMapsToSteam() {
    assertThat(Store.fromLudusaviTag("steam")).isEqualTo(Store.STEAM);
    assertThat(Store.fromLudusaviTag("Steam")).isEqualTo(Store.STEAM);
    assertThat(Store.fromLudusaviTag("STEAM")).isEqualTo(Store.STEAM);
  }

  @Test
  void nonSteamTagsMapToOther() {
    assertThat(Store.fromLudusaviTag("gog")).isEqualTo(Store.OTHER);
    assertThat(Store.fromLudusaviTag("epic")).isEqualTo(Store.OTHER);
    assertThat(Store.fromLudusaviTag("microsoft")).isEqualTo(Store.OTHER);
  }

  @Test
  void nullTagMapsToOther() {
    assertThat(Store.fromLudusaviTag(null)).isEqualTo(Store.OTHER);
  }
}
