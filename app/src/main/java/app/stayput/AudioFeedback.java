package app.stayput;

import android.content.Context;
import android.media.*;
import java.util.*;

public final class AudioFeedback {
  private static SoundPool pool;
  private static final Map<String, Integer> sounds = new HashMap<>();
  private static final Set<Integer> loaded = new HashSet<>();

  public static void init(Context c) {
    if (pool != null) return;
    pool =
        new SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            .build();
    pool.setOnLoadCompleteListener(
        (p, id, status) -> {
          if (status == 0) loaded.add(id);
        });
    for (String name : new String[] {"tap", "start", "complete"})
      try {
        android.content.res.AssetFileDescriptor fd = c.getAssets().openFd(name + ".wav");
        sounds.put(name, pool.load(fd, 1));
        fd.close();
      } catch (Exception ignored) {
      }
  }

  public static void play(Context c, String name) {
    init(c);
    if (!Store.bool(c, "feedback", true)) return;
    Integer id = sounds.get(name);
    if (id != null && loaded.contains(id)) pool.play(id, .4f, .4f, 1, 0, 1);
  }
}
