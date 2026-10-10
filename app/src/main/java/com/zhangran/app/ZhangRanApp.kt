/**
 * 账然应用入口（Application）。
 *
 * 职责：只承载 Hilt 的依赖注入容器（[HiltAndroidApp]）。
 * 禁止：在此保存业务状态（依据 docs/04 §20「Activity → 保存业务状态」同类约束与分层要求）。
 */
package com.zhangran.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZhangRanApp : Application()
