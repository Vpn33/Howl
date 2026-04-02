package com.example.howl

import androidx.compose.remote.creation.pow
import java.io.File
import kotlin.math.pow

object WaveUtil {
    // 常量定义
    val HZ_SLIDER = listOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
        31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 52,
        54, 56, 58, 60, 62, 64, 66, 68, 70, 72, 74, 76, 78, 80, 85, 90, 95, 100, 110, 120,
        130, 140, 150, 160, 170, 180, 190, 200, 233, 266, 300, 333, 366, 400, 450, 500, 550,
        600, 700, 800, 900, 1000)

    val STAGE_TIME_SLIDER = listOf(0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
        1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000,
        2100, 2200, 2300, 2400, 2500, 2600, 2700, 2800, 2900, 3000,
        3100, 3200, 3300, 3400, 3500, 3600, 3700, 3800, 3900, 4000,
        4100, 4200, 4300, 4400, 4500, 4600, 4700, 4800, 4900, 5000,
        5200, 5400, 5600, 5800, 6000, 6200, 6400, 6600, 6800, 7000,
        7200, 7400, 7600, 7800, 8000, 8500, 9000, 9500, 10000, 11000, 12000,
        13000, 14000, 15000, 16000, 17000, 18000, 19000, 20000,
        23300, 26600, 30000, 33300, 36600, 40000, 45000, 50000, 55000,
        60000, 70000, 80000, 90000, 100000, 120000, 140000, 160000,
        180000, 200000, 250000, 300000)

    const val WINDOW_TIME = 100
    const val WINDOW_TIME_DOUBLE = 100

    // 波形频率转换 波形文件中的频率是一个下标
    fun msToHz(ms: Int): Int {
        if (ms < 0) {
            return HZ_SLIDER[0]
        }
        if (ms >= HZ_SLIDER.size) {
            return HZ_SLIDER[HZ_SLIDER.size - 1]
        }
        return HZ_SLIDER[ms]
    }

    // 通过索引获取频率值
    fun idx2StageTime(idx: Int): Int {
        return STAGE_TIME_SLIDER.getOrElse(idx) { 1 }
    }

    // 小节时长转换
    fun toStageTime(time: Int): Int {
        return idx2StageTime(time)
    }

    // 生成V2波形
    fun genderV2Wave(info: CtrlItem): List<V2Model> {
        val v2ModelList = mutableListOf<V2Model>()
        val stageList = info.getStageList()
        for (stage in stageList) {
            // 启用了小节就计算V2Model
            if (stage.enabled) {
                val stageList1 = stageV2Exchange(stage)
                v2ModelList.addAll(stageList1)
            }
        }
        return v2ModelList
    }

    // V2小节转换波形
    fun stageV2Exchange(stage: WaveStage): List<V2Model> {
        val res = mutableListOf<V2Model>()

        // 小节内元的数量
        val metaCnt = stage.metas.size
        // 频率类型
        val hzType = stage.hzType
        // 最小频率
        var hzMin: Int
        // 最大频率
        var hzMax: Int
        // 频率数组缓存
        var hzTemp: List<Int>
        val hzArray = stage.getHz() ?: listOf(0, 0)
        // 每次渐变大小
        var grantNum = 0.0
        // 小节循环次数
        val stageLoopCnt = stage.getTimes()
        // 高低频平衡
        val balance = 8
        // 最后一个Z
        var lastZ = 0
        var f638n = 1
        var f640p = 99999

        // 频率类型 频率类型 1-固定 2-节间渐变 3-元内渐变 4-元间渐变 5-阶梯渐变 6-每节随机 7-每元随机
        if (hzType == 1) {
            hzMin = 0
            hzMax = hzArray[0]
        } else {
            // 渐变类型 0:小 -> 大 1:大 -> 小
            val hzGradient = stage.hzGradient.takeIf { it != 0 } ?: if (hzArray[0] - hzArray[1] >= 0) 1 else 0
            if (hzGradient == 0) {
                hzMin = hzArray[1]
                hzMax = hzArray[0]
            } else {
                hzMin = hzArray[0]
                hzMax = hzArray[1]
            }
        }
        hzTemp = listOf(hzMin, hzMax)

        for (i in 1..stage.getTimes()) {
            for (m in stage.metas) {
                hzMax = (hzTemp[1] * 20) + 1000
                hzMin = (hzTemp[0] * 20) + 1000

                // f638n和f640p 未破解出是什么参数
                f638n = kotlin.math.round((stageLoopCnt * (f638n - 1)).toDouble() / f640p).toInt() + 1
                if (f638n < 1) {
                    f638n = 1
                }
                f640p = stageLoopCnt

                // 计算下次渐变的大小
                grantNum = ((grantNum * metaCnt) + 1) / metaCnt.toDouble()

                // 1-固定 2-节间渐变 3-元内渐变 4-元间渐变 5-阶梯渐变 6-每节随机 7-每元随机
                if (hzType == 4) {
                    if (stageLoopCnt > 1) {
                        hzMax += ((hzMin - hzMax) * (f638n - 1)) / (stageLoopCnt - 1)
                    }
                } else if (hzType == 3) {
                    hzMax = (hzMax + (((hzMin - hzMax) * ((metaCnt * grantNum) - 1)) / (metaCnt - 1))).toInt()
                } else if (hzType == 2) {
                    hzMax = (hzMax + ((((hzMin - hzMax) * 1) * ((f638n + (((metaCnt * grantNum) - 1) / (metaCnt - 1))) - 1)) / stageLoopCnt)).toInt()
                }
                val frequency = Math.pow(10.0, hzMax.toDouble() / 1000.0).toInt()
                lastZ = m.y
                var x = (Math.pow(frequency.toDouble() / 1000.0, 0.5) * balance).toInt()
                if (x < 1) {
                    x = 1
                }
                val y = frequency - x
                val v2Model = V2Model(x, y, lastZ)
                res.add(v2Model)

                // 如果渐变>1说明小节内全部元都结束了 要重置渐变大小为0
                if (grantNum >= 1.0) {
                    grantNum = 0.0
                    f638n++
                    if (f638n > stageLoopCnt) {
                        f638n = 1
                    } else {
                        continue
                    }
                } else {
                    continue
                }
            }
        }

        return res
    }

    // 字符串转V2波形列表
    fun parseListStrToV2(str: String): List<V2Model> {
        val t = str.replace(Regex("\\s"), "").replace("\"[{", "[").replace("}]\"", "]").replace("\\\"", "\"")
        return try {
            // 这里简化处理，实际应该使用JSON解析
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 检查波形时长并补全
    fun checkWaveDurtime(time: Long, msArray: MutableList<V3Model>) {
        // 说明波形不够长 需要补全
        val waveDruTime = msArray.size * WINDOW_TIME
        val minDurationTime = time
        if (waveDruTime < minDurationTime) {
            // 计算持续时间内还可以放下几个波形 上取整  -1是去掉默认的
            var loopCnt = kotlin.math.floor(minDurationTime.toDouble() / waveDruTime).toInt() - 1
            // 需要保证波形完整播放
            if (minDurationTime % waveDruTime > 0) {
                loopCnt += 1
            }
            // 拷贝一个完整的数据
            val padList = msArray.toList()
            // 补全剩余时间中的波形循环
            for (i in 0 until loopCnt) {
                msArray.addAll(padList)
            }
        }
    }

    fun getRoundIntValue(value: Double): Int {
        return kotlin.math.round(value).toInt()
    }

    // V2波形转V3波形
    fun v2ToV3(v2List: List<V2Model>): List<V3Model> {
        val v3ModelList = mutableListOf<V3Model>()
        for (v2 in v2List) {
            // V3波形频率 = V2 (X + Y) 后，执行(10 ~ 1000) -> (10 ~ 240)的转化
            val hz = v2.x + v2.y
            var v3Hz = 0
            if (hz in 10..100) {
            v3Hz = hz
        } else if (hz in 101..600) {
            v3Hz = ((hz - 100) / 5.0 + 100).toInt()
        } else if (hz in 601..1000) {
            v3Hz = ((hz - 600) / 10.0 + 200).toInt()
        } else {
            v3Hz = 10
        }

            // V3波形强度 = V2 (Z * 5)
            // pluse波形文件的z已经是V3的值了，这里不用再*5了 直接使用就可以
            val v3Z = v2.z
            val v3 = V3Model(v3Hz, v3Z)

            v3ModelList.add(v3)
        }
        return v3ModelList
    }

    // 毫秒转换成可视化的时间格式
    fun msToViewTimeStr(milliseconds: Long): String {
        if (milliseconds < 1000) {
            return "$milliseconds ms"
        }
        if (milliseconds < 60000) {
            val seconds = milliseconds / 1000.0
            val rounded = kotlin.math.round(seconds * 100) / 100.0
            return if (kotlin.math.round(rounded) - rounded == 0.0) {
                "${kotlin.math.round(rounded).toInt()}秒"
            } else {
                "${rounded}秒"
            }
        }
        val hours = (milliseconds / (1000 * 60 * 60)).toInt()
        val remainingMillis = milliseconds - (hours * 1000 * 60 * 60)
        val minutes = (remainingMillis / (1000 * 60)).toInt()
        val seconds = ((remainingMillis - (minutes * 1000 * 60)) / 1000).toInt()
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    // 解析pulse文件内容为CtrlItem对象
    fun parsePulseToCtrlItem(pulseName: String, pulseContent: String): CtrlItem {
        val ctrlItem = CtrlItem()

        // 移除换行符，将整个内容作为一行处理
        val content = pulseContent.trim().replace("\n", "")

        // 例： Dungeonlab+pulse:0,1,16=59,20,39,1,1/100.00-1,100.00-1+section+69,0,38,1,1/65.00-1,65.00-1+section+19,0,37,1,1/100.00-1,100.00-1+section+72,0,39,1,1/0.00-1,0.00-1,100.00-1+section+20,0,40,1,1/100.00-1,100.00-1
        // 解析标题行
        if (content.startsWith("Dungeonlab+pulse:")) {
            // 分割基本信息和小节部分
            val parts = content.split("Dungeonlab+pulse:")

            if (parts.size == 2) {
                var pulseData = parts[1]
                if (pulseData.indexOf('=') < 0) {
                    // 休息时长
                    ctrlItem.restTime = 0
                    // 播放速率
                    ctrlItem.rate = 1
                    // 第三个值8/16都有 暂时未发现这个值有什么用 这里不解析
                } else {
                    val pulseDataParts = pulseData.split('=')
                    val itemInfo = pulseDataParts[0].split(',')
                    // 休息时长
                    ctrlItem.restTime = itemInfo[0].toIntOrNull() ?: 0
                    // 播放速率
                    ctrlItem.rate = itemInfo[1].toIntOrNull() ?: 1
                    // 继续解析后续的波形小节信息
                    pulseData = pulseDataParts[1]
                }

                // 使用+section+分割所有小节
                val sectionParts = pulseData.split("+section+")

                // 解析小节列表
                val stageList = mutableListOf<WaveStage>()

                // 每个sectionParts元素代表一个小节
                for (sectionPart in sectionParts) {
                    if (sectionPart.trim().isNotEmpty()) { // 跳过空小节
                        val sectionInfoAndMetas = sectionPart.split('/')
                        if (sectionInfoAndMetas.size == 2) {
                            val sectionInfo = sectionInfoAndMetas[0]
                            val sectionMetas = sectionInfoAndMetas[1]
                            val section = parseStageData(sectionInfo, sectionMetas)
                            if (section != null) {
                                stageList.add(section)
                            }
                        }
                    }
                }

                // 设置节点列表
                ctrlItem.setStageList(stageList)

                // 生成默认名称
                ctrlItem.name = pulseName

                // 计算总时长
                ctrlItem.getDuration()
            }
        }

        return ctrlItem
    }

    // 解析单个小节的数据行
    private fun parseStageData(stageInfo: String, stageMetas: String): WaveStage? {
        // 创建小节对象
        val stage = WaveStage()

        // 解析小节的基本信息
        if (stageInfo.isBlank()) {
            return null
        }
        val stageParams = stageInfo.split(',')
        // 根据用户提供的规则，最少应该有5个参数
        if (stageParams.size < 5) {
            return null
        }

        // 正确的解析顺序：
        // 频率值1,频率值2,小节时长,频率类型,小节状态
        // 计算频率范围
        val hzArray = listOf(msToHz(stageParams[0].toIntOrNull() ?: 0), msToHz(stageParams[1].toIntOrNull() ?: 0))
        // 渐变类型 0:小 -> 大 1:大 -> 小
        val hzGradient = if (hzArray[0] - hzArray[1] >= 1) 1 else 0
        var hzMin: Int
        var hzMax: Int

        if (hzGradient == 1) {
            hzMin = hzArray[1]
            hzMax = hzArray[0]
        } else {
            hzMin = hzArray[0]
            hzMax = hzArray[1]
        }

        // 设置频率数组
        stage.hzMin = hzMin
        stage.hzMax = hzMax
        stage.hzGradient = hzGradient
        stage.setHz(hzArray)
        // 小节时长(滑块)
        stage.setStageTime(stageParams[2].toIntOrNull() ?: 0)
        // 频率类型 (1-固定 2-节间渐变 3-元内渐变 4-元间渐变)
        stage.hzType = stageParams[3].toIntOrNull() ?: 1
        // 小节状态 0-禁用 1-启用
        stage.enabled = stageParams[4].toIntOrNull() == 1

        // 解析元数据
        if (stageMetas.isNotBlank()) {
            val metaGroups = stageMetas.split(',')
            for (metaGroup in metaGroups) {
                if (metaGroup.isNotBlank()) {
                    val metaParams = metaGroup.split('-')
                    if (metaParams.size >= 2) {
                        // 创建小节对象
                        val y = metaParams[0].toDoubleOrNull()?.toInt() ?: 100
                        val anchor = metaParams[1].toIntOrNull() ?: 0
                        val meta = WaveMeta(y, anchor)
                        stage.metas.add(meta)
                    }
                }
            }
        }
        // 如果启用 才需要计算循环次数
        if (stage.enabled && stage.metas.isNotEmpty()) {
            // 小节持续时间
            val st = toStageTime(stage.getStageTime())
            // 脉冲元 * 窗口时间 = 小节一次脉冲时间
            val metaTime = stage.metas.size * 100
            // 上取整 就是小节循环次数
            stage.setTimes(kotlin.math.ceil(st.toDouble() / metaTime).toInt())
        }

        return stage
    }
}