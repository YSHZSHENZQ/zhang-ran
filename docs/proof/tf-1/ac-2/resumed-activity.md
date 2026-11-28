# AC-2 首页返回离开前台

操作（不改产品代码）：`pm clear` 后冷启动 `com.zhangran.app/.MainActivity`，点「完成」跳过权限引导，停在首页，再 `input keyevent 4`。截图是 `adb exec-out screencap`。

返回前 resumed：

```
    topResumedActivity=ActivityRecord{105966323 u0 com.zhangran.app/.MainActivity t22}
  ResumedActivity: ActivityRecord{105966323 u0 com.zhangran.app/.MainActivity t22}
  mFocusedApp=ActivityRecord{105966323 u0 com.zhangran.app/.MainActivity t22}
```

返回后 resumed：

```
      topResumedActivity=ActivityRecord{97933804 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t6}
  ResumedActivity: ActivityRecord{97933804 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t6}
  mFocusedApp=ActivityRecord{97933804 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t6}
```

返回后前台是 Nexus Launcher，账然不在前台。完整 `dumpsys activity activities` 见同目录 `dumpsys-before.txt` 与 `dumpsys-after.txt`。
