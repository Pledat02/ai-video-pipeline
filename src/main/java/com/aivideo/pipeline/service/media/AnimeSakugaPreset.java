package com.aivideo.pipeline.service.media;

import java.util.Locale;

/** Camera choreography shared by image generators for the Anime Sakuga preset. */
final class AnimeSakugaPreset {
    private static final String[] SHOTS = {
            "tight face push-in, eyes snap toward the objective, hair and sweat moving",
            "medium lateral tracking shot, full sprint, strong left-to-right screen direction",
            "extreme ground-level insert, feet and the key action fill the foreground",
            "close side angle, hard body feint with dramatic foreshortening",
            "low tracking medium shot, explosive acceleration and environmental debris",
            "tight frontal compression shot, opponent or obstacle dominates the foreground",
            "extreme ground insert at the decisive contact, small white impact flash",
            "medium orbital camera move around the subject, curved motion trail",
            "snap-zoom reaction close-up, sudden calm before the climax",
            "low over-the-shoulder angle, body coils for the decisive action",
            "extreme close-up of impact, graphic impact rings and speed-line burst",
            "settling medium-wide payoff shot, clear result and explosive emotional release"
    };

    private AnimeSakugaPreset() {}

    static boolean enabled(String imageStyle) {
        String normalized = imageStyle == null ? "" : imageStyle.toLowerCase(Locale.ROOT);
        return normalized.contains("anime sakuga") || normalized.contains("high-sakuga");
    }

    static String shotDirection(int index, int count) {
        int mapped = count <= 1 ? 0 : Math.round(index * (SHOTS.length - 1f) / (count - 1f));
        return " Camera choreography: " + SHOTS[Math.max(0, Math.min(mapped, SHOTS.length - 1))]
                + ". High-sakuga anime action, clean dimensional linework, wide foreshortening, "
                + "speed lines, frozen debris and sweat at action peaks. Maintain character identity, "
                + "spatial continuity and consistent screen direction with adjacent shots. No captions.";
    }
}
