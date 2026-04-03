package com.example.howl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class WaveViewModel : ViewModel() {
    // 所有可用的波形列表
    private val _availableWaves = MutableStateFlow<List<WaveInfo>>(emptyList())
    val availableWaves: StateFlow<List<WaveInfo>> = _availableWaves.asStateFlow()

    // 波形缓存，用于存储已解析的波形数据，避免重复读取和解析
    private val waveCache = mutableMapOf<String, String>() // key: 波形路径, value: 波形文件内容
    private val ctrlItemCache = mutableMapOf<String, CtrlItem>() // key: 波形路径, value: 解析后的CtrlItem

    // 状态
    private val _state = MutableStateFlow(WaveState())
    val state: StateFlow<WaveState> = _state.asStateFlow()

    // 对话框状态
    private val _showWaveSelectionDialog = MutableStateFlow(false)
    val showWaveSelectionDialog: StateFlow<Boolean> = _showWaveSelectionDialog.asStateFlow()

    private val _currentChannelForSelection = MutableStateFlow("A")
    val currentChannelForSelection: StateFlow<String> = _currentChannelForSelection.asStateFlow()

    // 删除确认对话框状态
    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _waveToDelete = MutableStateFlow<WaveInfo?>(null)
    val waveToDelete: StateFlow<WaveInfo?> = _waveToDelete.asStateFlow()

    private val _channelForDelete = MutableStateFlow("A")
    val channelForDelete: StateFlow<String> = _channelForDelete.asStateFlow()

    init {
        // 初始化加载内置波形
        loadBuiltInWaves()
    }

    // 从SharedPreferences加载保存的状态
    fun loadSavedState(context: Context) {
        val prefs = context.getSharedPreferences("WavePrefs", Context.MODE_PRIVATE)
        val gson = Gson()

        // 加载A通道已选择的波形
        val aChannelWavesJson = prefs.getString("aChannelSelectedWaves", null)
        val aChannelWaves: List<WaveInfo> = if (aChannelWavesJson != null) {
            val type = object : TypeToken<List<WaveInfo>>() {}.type
            gson.fromJson<List<WaveInfo>>(aChannelWavesJson, type)
        } else {
            emptyList()
        }

        // 加载B通道已选择的波形
        val bChannelWavesJson = prefs.getString("bChannelSelectedWaves", null)
        val bChannelWaves: List<WaveInfo> = if (bChannelWavesJson != null) {
            val type = object : TypeToken<List<WaveInfo>>() {}.type
            gson.fromJson<List<WaveInfo>>(bChannelWavesJson, type)
        } else {
            emptyList()
        }

        // 加载播放模式和播放时间
        val aChannelPlayMode = prefs.getString("aChannelPlayMode", "列表循环") ?: "列表循环"
        val bChannelPlayMode = prefs.getString("bChannelPlayMode", "列表循环") ?: "列表循环"
        val aChannelPlayTime = prefs.getString("aChannelPlayTime", "10秒") ?: "10秒"
        val bChannelPlayTime = prefs.getString("bChannelPlayTime", "10秒") ?: "10秒"
        
        // 根据语言设置转换为对应的语言
        val language = Prefs.language.value
        val convertedAChannelPlayMode = when (language) {
            "en" -> {
                when (aChannelPlayMode) {
                    "列表循环" -> "List Loop"
                    "单曲循环" -> "Single Loop"
                    "随机" -> "Random"
                    else -> aChannelPlayMode
                }
            }
            "zh" -> {
                when (aChannelPlayMode) {
                    "List Loop" -> "列表循环"
                    "Single Loop" -> "单曲循环"
                    "Random" -> "随机"
                    else -> aChannelPlayMode
                }
            }
            else -> aChannelPlayMode
        }
        
        val convertedBChannelPlayMode = when (language) {
            "en" -> {
                when (bChannelPlayMode) {
                    "列表循环" -> "List Loop"
                    "单曲循环" -> "Single Loop"
                    "随机" -> "Random"
                    else -> bChannelPlayMode
                }
            }
            "zh" -> {
                when (bChannelPlayMode) {
                    "List Loop" -> "列表循环"
                    "Single Loop" -> "单曲循环"
                    "Random" -> "随机"
                    else -> bChannelPlayMode
                }
            }
            else -> bChannelPlayMode
        }
        
        val convertedAChannelPlayTime = when (language) {
            "en" -> {
                when (aChannelPlayTime) {
                    "5秒" -> "5s"
                    "10秒" -> "10s"
                    "30秒" -> "30s"
                    "60秒" -> "60s"
                    "120秒" -> "120s"
                    else -> aChannelPlayTime
                }
            }
            "zh" -> {
                when (aChannelPlayTime) {
                    "5s" -> "5秒"
                    "10s" -> "10秒"
                    "30s" -> "30秒"
                    "60s" -> "60秒"
                    "120s" -> "120秒"
                    else -> aChannelPlayTime
                }
            }
            else -> aChannelPlayTime
        }
        
        val convertedBChannelPlayTime = when (language) {
            "en" -> {
                when (bChannelPlayTime) {
                    "5秒" -> "5s"
                    "10秒" -> "10s"
                    "30秒" -> "30s"
                    "60秒" -> "60s"
                    "120秒" -> "120s"
                    else -> bChannelPlayTime
                }
            }
            "zh" -> {
                when (bChannelPlayTime) {
                    "5s" -> "5秒"
                    "10s" -> "10秒"
                    "30s" -> "30秒"
                    "60s" -> "60秒"
                    "120s" -> "120秒"
                    else -> bChannelPlayTime
                }
            }
            else -> bChannelPlayTime
        }

        // 更新状态
        _state.value = WaveState(
            aChannelPlaying = false, // 重启后默认停止播放
            bChannelPlaying = false, // 重启后默认停止播放
            aChannelPlayMode = convertedAChannelPlayMode,
            bChannelPlayMode = convertedBChannelPlayMode,
            aChannelPlayTime = convertedAChannelPlayTime,
            bChannelPlayTime = convertedBChannelPlayTime,
            aChannelSelectedWaves = aChannelWaves,
            bChannelSelectedWaves = bChannelWaves,
            aChannelPlayIndex = 0, // 重启后重置播放索引
            bChannelPlayIndex = 0, // 重启后重置播放索引
            aChannelPlayElapsedTime = 0.0, // 重启后重置播放时间
            bChannelPlayElapsedTime = 0.0 // 重启后重置播放时间
        )
    }

    // 保存状态到SharedPreferences
    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("WavePrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = Gson()

        val currentState = _state.value

        // 保存A通道已选择的波形
        val aChannelWavesJson = gson.toJson(currentState.aChannelSelectedWaves)
        editor.putString("aChannelSelectedWaves", aChannelWavesJson)

        // 保存B通道已选择的波形
        val bChannelWavesJson = gson.toJson(currentState.bChannelSelectedWaves)
        editor.putString("bChannelSelectedWaves", bChannelWavesJson)

        // 保存播放模式和播放时间
        editor.putString("aChannelPlayMode", currentState.aChannelPlayMode)
        editor.putString("bChannelPlayMode", currentState.bChannelPlayMode)
        editor.putString("aChannelPlayTime", currentState.aChannelPlayTime)
        editor.putString("bChannelPlayTime", currentState.bChannelPlayTime)

        editor.apply()
    }

    // 加载内置波形
    private fun loadBuiltInWaves() {
        // 内置波形现在从assets/pulse目录加载
        // 注意：在运行时需要使用AssetManager来读取
        println("内置波形从assets/pulse目录加载")
        // 这里暂时设置为空，后续需要在应用启动时通过AssetManager加载
        _availableWaves.value = emptyList()
    }

    // 从assets目录加载内置波形
    fun loadBuiltInWavesFromAssets(context: Context) {
        viewModelScope.launch {
            try {
                val assetManager = context.assets
                val pulseFiles =
                    assetManager.list("pulse")?.filter { it.endsWith(".pulse") } ?: emptyList()

                println("从assets/pulse目录找到 ${pulseFiles.size} 个波形文件")

                val waveInfos = pulseFiles.map { fileName ->
                    val name = fileName.removeSuffix(".pulse")
                    WaveInfo(
                        name = name,
                        path = "assets/pulse/$fileName"
                    )
                }

                println("加载的波形数量: ${waveInfos.size}")
                _availableWaves.value = waveInfos
            } catch (e: Exception) {
                println("加载内置波形失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // 加载本地文件夹中的波形
    fun loadWavesFromFolder(folderPath: String) {
        val folder = File(folderPath)
        if (folder.exists() && folder.isDirectory) {
            val waveFiles = folder.listFiles { _, name -> name.endsWith(".pulse") }
            val waveInfos = waveFiles?.map { file ->
                WaveInfo(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath
                )
            } ?: emptyList()
            _availableWaves.value = mergeWaveInfos(_availableWaves.value, waveInfos)
        }
    }

    // 从URI加载本地文件夹中的波形
    fun loadWavesFromFolderUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver

                // 获得持久访问权限
                val takeFlags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                // 查询文件夹中的文件
                val childrenUri =
                    android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        uri, android.provider.DocumentsContract.getTreeDocumentId(uri)
                    )

                val waveInfos = mutableListOf<WaveInfo>()

                // 打印调试信息
                println("开始查询文件夹: $uri")

                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    println("查询结果数量: ${cursor.count}")

                    val nameIndex =
                        cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val idIndex =
                        cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeTypeIndex =
                        cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex)
                        val docId = cursor.getString(idIndex)
                        val mimeType = cursor.getString(mimeTypeIndex)

                        println("文件: $name, MIME类型: $mimeType")

                        if (name.endsWith(".pulse")) {
                            val fileUri =
                                android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                    uri,
                                    docId
                                )
                            waveInfos.add(
                                WaveInfo(
                                    name = name.removeSuffix(".pulse"),
                                    path = fileUri.toString()
                                )
                            )
                            println("添加波形: ${name.removeSuffix(".pulse")}")
                        }
                    }
                }

                println("加载的波形数量: ${waveInfos.size}")
                _availableWaves.value = mergeWaveInfos(_availableWaves.value, waveInfos)
            } catch (e: Exception) {
                println("加载波形失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // 合并波形信息，替换重复的波形
    private fun mergeWaveInfos(
        existingWaves: List<WaveInfo>,
        newWaves: List<WaveInfo>
    ): List<WaveInfo> {
        val existingWaveMap = existingWaves.associateBy { it.name }
        val newWaveMap = newWaves.associateBy { it.name }

        // 合并波形，新波形会替换同名的旧波形
        val mergedWaveMap = existingWaveMap + newWaveMap

        return mergedWaveMap.values.toList()
    }

    // 显示波形选择对话框
    fun showWaveSelectionDialog(channel: String) {
        _currentChannelForSelection.value = channel
        _showWaveSelectionDialog.value = true
    }

    // 关闭波形选择对话框
    fun dismissWaveSelectionDialog() {
        _showWaveSelectionDialog.value = false
    }

    // 显示删除确认对话框
    fun showDeleteConfirmDialog(channel: String, wave: WaveInfo) {
        _channelForDelete.value = channel
        _waveToDelete.value = wave
        _showDeleteConfirmDialog.value = true
    }

    // 关闭删除确认对话框
    fun dismissDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = false
        _waveToDelete.value = null
    }

    // 确认删除波形
    fun confirmDeleteWave(context: Context) {
        val channel = _channelForDelete.value
        val wave = _waveToDelete.value ?: return

        // 清除被删除波形的缓存
        waveCache.remove(wave.path)
        ctrlItemCache.remove(wave.path)

        // 先停止播放
        toggleChannelPlay(context, channel, false)

        // 删除波形
        val currentState = _state.value
        val updatedWaves = when (channel) {
            "A" -> currentState.aChannelSelectedWaves.filter { it != wave }
            "B" -> currentState.bChannelSelectedWaves.filter { it != wave }
            else -> return
        }

        _state.update {
            when (channel) {
                "A" -> it.copy(
                    aChannelSelectedWaves = updatedWaves,
                    aChannelPlayIndex = 0,
                    aChannelPlayElapsedTime = 0.0
                )

                "B" -> it.copy(
                    bChannelSelectedWaves = updatedWaves,
                    bChannelPlayIndex = 0,
                    bChannelPlayElapsedTime = 0.0
                )

                else -> it
            }
        }

        // 保存状态
        saveState(context)

        dismissDeleteConfirmDialog()
    }

    // 选择波形
    fun selectWaves(waveInfos: List<WaveInfo>, context: Context) {
        val channel = _currentChannelForSelection.value
        _state.update {
            when (channel) {
                "A" -> it.copy(
                    aChannelSelectedWaves = waveInfos,
                    aChannelPlayIndex = 0,
                    aChannelPlayElapsedTime = 0.0
                )

                "B" -> it.copy(
                    bChannelSelectedWaves = waveInfos,
                    bChannelPlayIndex = 0,
                    bChannelPlayElapsedTime = 0.0
                )

                else -> it
            }
        }

        // 保存状态
        saveState(context)

        dismissWaveSelectionDialog()
    }

    // 切换通道播放状态
    fun toggleChannelPlay(context: Context, channel: String, play: Boolean? = null) {
        val currentState = _state.value
        val isPlaying = when (channel) {
            "A" -> currentState.aChannelPlaying
            "B" -> currentState.bChannelPlaying
            else -> false
        }
        val newPlaying = play ?: !isPlaying

        _state.update {
            when (channel) {
                "A" -> it.copy(aChannelPlaying = newPlaying)
                "B" -> it.copy(bChannelPlaying = newPlaying)
                else -> it
            }
        }

        // 生成播放数据（同时处理A、B通道）
        generateChannelPlayData(context)
    }

    // 切换到指定索引的波形并播放
    fun switchToWave(context: Context, channel: String, waveIndex: Int) {
        val currentState = _state.value
        val waves = when (channel) {
            "A" -> currentState.aChannelSelectedWaves
            "B" -> currentState.bChannelSelectedWaves
            else -> return
        }

        // 检查索引是否有效
        if (waveIndex < 0 || waveIndex >= waves.size) return

        // 更新状态：切换到指定波形并重置播放时间
        _state.update {
            when (channel) {
                "A" -> it.copy(
                    aChannelPlayIndex = waveIndex,
                    aChannelPlayElapsedTime = 0.0,
                    aChannelPlaying = true
                )

                "B" -> it.copy(
                    bChannelPlayIndex = waveIndex,
                    bChannelPlayElapsedTime = 0.0,
                    bChannelPlaying = true
                )

                else -> it
            }
        }

        // 生成播放数据
        generateChannelPlayData(context)
    }

    // 生成通道播放数据
    private fun generateChannelPlayData(context: Context) {
        viewModelScope.launch {
            val currentState = _state.value

            // 获取A、B通道的波形
            val aChannelWaves = currentState.aChannelSelectedWaves
            val bChannelWaves = currentState.bChannelSelectedWaves

            // 准备A、B通道的V3模型列表
            var aChannelV3List = emptyList<V3Model>()
            var bChannelV3List = emptyList<V3Model>()

            // 处理A通道
            if (currentState.aChannelPlaying && aChannelWaves.isNotEmpty()) {
                val aWave = aChannelWaves[currentState.aChannelPlayIndex]
                val aCtrlItem = getCtrlItem(context, aWave)
                if (aCtrlItem != null) {
                    val aV3ModelList = aCtrlItem.getV3ModelList()

                    // 处理双通道情况
                    aChannelV3List = when (aV3ModelList) {
                        is List<*> -> aV3ModelList.filterIsInstance<V3Model>()
                        is Map<*, *> -> {
                            // 双通道波形，同时获取A、B通道数据
                            aChannelV3List = aV3ModelList["a"] as? List<V3Model> ?: emptyList()
                            bChannelV3List = aV3ModelList["b"] as? List<V3Model> ?: emptyList()
                            aChannelV3List
                        }

                        else -> emptyList()
                    }
                }
            }

            // 处理B通道（如果A通道不是双通道）
            if (currentState.bChannelPlaying && bChannelWaves.isNotEmpty() && bChannelV3List.isEmpty()) {
                val bWave = bChannelWaves[currentState.bChannelPlayIndex]
                val bCtrlItem = getCtrlItem(context, bWave)
                if (bCtrlItem != null) {
                    val bV3ModelList = bCtrlItem.getV3ModelList()

                    // 处理双通道情况
                    bChannelV3List = when (bV3ModelList) {
                        is List<*> -> bV3ModelList.filterIsInstance<V3Model>()
                        is Map<*, *> -> {
                            // 双通道波形，同时获取A、B通道数据
                            if (aChannelV3List.isEmpty()) {
                                aChannelV3List = bV3ModelList["a"] as? List<V3Model> ?: emptyList()
                            }
                            bV3ModelList["b"] as? List<V3Model> ?: emptyList()
                        }

                        else -> emptyList()
                    }
                }
            }

            // 创建波形脉冲源并启动播放
            if ((currentState.aChannelPlaying || currentState.bChannelPlaying) &&
                (aChannelV3List.isNotEmpty() || bChannelV3List.isNotEmpty())
            ) {

                val wavePulseSource = WavePulseSource(
                    viewModel = this@WaveViewModel,
                    context = context,
                    aChannelV3List = aChannelV3List,
                    bChannelV3List = bChannelV3List,
                    aChannelWaves = aChannelWaves,
                    bChannelWaves = bChannelWaves,
                    aChannelPlayMode = currentState.aChannelPlayMode,
                    bChannelPlayMode = currentState.bChannelPlayMode,
                    aChannelPlayTime = currentState.aChannelPlayTime,
                    bChannelPlayTime = currentState.bChannelPlayTime,
                    aChannelPlayIndex = currentState.aChannelPlayIndex,
                    bChannelPlayIndex = currentState.bChannelPlayIndex,
                    aChannelPlayElapsedTime = currentState.aChannelPlayElapsedTime,
                    bChannelPlayElapsedTime = currentState.bChannelPlayElapsedTime
                )
                Player.switchPulseSource(wavePulseSource)
                Player.startPlayer()

                println("启动波形播放: A通道 ${aChannelV3List.size} 个数据点, B通道 ${bChannelV3List.size} 个数据点")
            } else {
                // 停止播放
                Player.stopPlayer()
                println("停止波形播放")
            }
        }
    }

    // 读取pulse文件内容
    private fun readPulseFile(context: Context, path: String): String? {
        // 先检查缓存
        if (waveCache.containsKey(path)) {
            return waveCache[path]
        }

        return try {
            if (path.startsWith("assets/")) {
                // 从assets目录读取
                val assetPath = path.removePrefix("assets/")
                val inputStream = context.assets.open(assetPath)
                val content = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()
                // 缓存文件内容
                waveCache[path] = content
                content
            } else if (path.startsWith("content://")) {
                // 从Content URI读取
                val uri = android.net.Uri.parse(path)
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() }
                inputStream?.close()
                // 缓存文件内容
                content?.let { waveCache[path] = it }
                content
            } else {
                // 从本地文件读取
                val file = File(path)
                if (file.exists()) {
                    val content = file.readText()
                    // 缓存文件内容
                    waveCache[path] = content
                    content
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            println("读取文件失败: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // 获取CtrlItem，使用缓存避免重复解析
    fun getCtrlItem(context: Context, wave: WaveInfo): CtrlItem? {
        // 先检查缓存
        if (ctrlItemCache.containsKey(wave.path)) {
            return ctrlItemCache[wave.path]
        }

        // 读取文件内容
        val content = readPulseFile(context, wave.path)
        if (content != null) {
            // 解析波形文件
            val ctrlItem = WaveUtil.parsePulseToCtrlItem(wave.name, content)
            // 缓存解析结果
            ctrlItemCache[wave.path] = ctrlItem
            return ctrlItem
        }
        return null
    }

    // 生成Pulse数据
    private fun generatePulseData(v3ModelList: Any, channel: String): List<Pulse> {
        val pulseList = mutableListOf<Pulse>()

        // 处理V3模型列表
        val v3List: List<V3Model> = when (v3ModelList) {
            is List<*> -> v3ModelList.filterIsInstance<V3Model>()
            is Map<*, *> -> {
                // 双通道情况
                if (channel == "A") {
                    v3ModelList["a"] as? List<V3Model> ?: emptyList()
                } else {
                    v3ModelList["b"] as? List<V3Model> ?: emptyList()
                }
            }

            else -> emptyList()
        }

        // 将V3模型转换为Pulse对象
        for (v3 in v3List) {
            // V3模型的hz是频率，z是强度
            // 转换为Pulse格式：ampA/ampB是强度(0-100)，freqA/freqB是频率(10-1000)
            val pulse = if (channel == "A") {
                Pulse(
                    ampA = v3.z.toFloat(),
                    ampB = 0f,
                    freqA = v3.hz.toFloat(),
                    freqB = 0f
                )
            } else {
                Pulse(
                    ampA = 0f,
                    ampB = v3.z.toFloat(),
                    freqA = 0f,
                    freqB = v3.hz.toFloat()
                )
            }
            pulseList.add(pulse)
        }

        return pulseList
    }

    // 波形脉冲源
    class WavePulseSource(
        private val viewModel: WaveViewModel,
        private val context: Context,
        private val aChannelV3List: List<V3Model>,
        private val bChannelV3List: List<V3Model>,
        private val aChannelWaves: List<WaveInfo>,
        private val bChannelWaves: List<WaveInfo>,
        private val aChannelPlayMode: String,
        private val bChannelPlayMode: String,
        private val aChannelPlayTime: String,
        private val bChannelPlayTime: String,
        private var aChannelPlayIndex: Int,
        private var bChannelPlayIndex: Int,
        private var aChannelPlayElapsedTime: Double,
        private var bChannelPlayElapsedTime: Double
    ) : PulseSource {
        override val displayName: String = "波形播放"
        override val duration: Double? = null
        override val isFinite: Boolean = false
        override val shouldLoop: Boolean = true
        override val readyToPlay: Boolean = true
        override val isRemote: Boolean = false

        private var aChannelIdx = 0
        private var bChannelIdx = 0
        private var lastUpdateTime = 0.0
        private var isChannelAGenerating = false
        private var isChannelBGenerating = false

        // 同步时间到ViewModel
        private fun syncTimeToViewModel() {
            val currentState = viewModel.state.value
            aChannelPlayElapsedTime = currentState.aChannelPlayElapsedTime
            bChannelPlayElapsedTime = currentState.bChannelPlayElapsedTime
        }

        override fun getPulseAtTime(time: Double): Pulse {
            // 计算时间差
            val deltaTime = if (lastUpdateTime > 0) time - lastUpdateTime else 0.01
            lastUpdateTime = time

            // 从ViewModel同步最新的播放时间
            syncTimeToViewModel()

            // 更新播放时间
            aChannelPlayElapsedTime += deltaTime
            bChannelPlayElapsedTime += deltaTime

            // 将更新后的时间同步回ViewModel
            viewModel.updateElapsedTime(aChannelPlayElapsedTime, bChannelPlayElapsedTime)

            // 检查是否需要切换A通道波形
            if (aChannelWaves.isNotEmpty() && !isChannelAGenerating) {
                val currentAWave = aChannelWaves[aChannelPlayIndex]
                val aCtrlItem = viewModel.getCtrlItem(context, currentAWave)
                if (aCtrlItem != null) {
                    val aDuration = aCtrlItem.getTotalTime() / 1000.0 // 转换为秒
                    val aMinPlayTime = viewModel.parsePlayTime(aChannelPlayTime).toDouble()
                    // 检查是否需要切换波形
                    // 情况1：波形时长大于等于最小播放时间，且已经播放完毕
                    // 情况2：波形时长小于最小播放时间，但已经达到最小播放时间
                    val shouldSwitch =
                        (aDuration >= aMinPlayTime && aChannelPlayElapsedTime >= aDuration) ||
                                (aDuration < aMinPlayTime && aChannelPlayElapsedTime >= aMinPlayTime)

                    if (shouldSwitch) {
                        // 切换到下一个波形
                        val newAChannelPlayIndex = viewModel.getNextPlayIndex(
                            aChannelPlayMode,
                            aChannelPlayIndex,
                            aChannelWaves.size
                        )
                        isChannelAGenerating = true

                        // 更新viewModel的状态
                        viewModel.updatePlayState(
                            channel = "A",
                            playIndex = newAChannelPlayIndex,
                            playElapsedTime = 0.0
                        )

                        // 重新生成通道数据
                        viewModel.generateChannelPlayData(context)
                        isChannelAGenerating = false

                    }
                }
            }

            // 检查是否需要切换B通道波形
            if (bChannelWaves.isNotEmpty() && !isChannelBGenerating) {
                val currentBWave = bChannelWaves[bChannelPlayIndex]
                val bCtrlItem = viewModel.getCtrlItem(context, currentBWave)
                if (bCtrlItem != null) {
                    val bDuration = bCtrlItem.getTotalTime() / 1000.0 // 转换为秒
                    val bMinPlayTime = viewModel.parsePlayTime(bChannelPlayTime).toDouble()

                    // 检查是否需要切换波形
                    // 情况1：波形时长大于等于最小播放时间，且已经播放完毕
                    // 情况2：波形时长小于最小播放时间，但已经达到最小播放时间
                    val shouldSwitch =
                        (bDuration >= bMinPlayTime && bChannelPlayElapsedTime >= bDuration) ||
                                (bDuration < bMinPlayTime && bChannelPlayElapsedTime >= bMinPlayTime)

                    if (shouldSwitch) {
                        // 切换到下一个波形
                        val newBChannelPlayIndex = viewModel.getNextPlayIndex(
                            bChannelPlayMode,
                            bChannelPlayIndex,
                            bChannelWaves.size
                        )
                        // 重新生成通道数据
                        isChannelBGenerating = true

                        // 更新viewModel的状态
                        viewModel.updatePlayState(
                            channel = "B",
                            playIndex = newBChannelPlayIndex,
                            playElapsedTime = 0.0
                        )
                        viewModel.generateChannelPlayData(context)
                        isChannelBGenerating = false
                    }
                }
            }

            // 循环获取A通道数据
            val aV3 = if (aChannelV3List.isNotEmpty()) {
                aChannelV3List[aChannelIdx++ % aChannelV3List.size]
            } else {
                V3Model(0, 0)
            }

            // 循环获取B通道数据
            val bV3 = if (bChannelV3List.isNotEmpty()) {
                bChannelV3List[bChannelIdx++ % bChannelV3List.size]
            } else {
                V3Model(0, 0)
            }

            // 生成包含A、B通道数据的Pulse对象
            return Pulse(
                ampA = aV3.z.toFloat() / 100.0f, // 将0-100的强度值转换为0.0-1.0的范围
                ampB = bV3.z.toFloat() / 100.0f, // 将0-100的强度值转换为0.0-1.0的范围
                freqA = (aV3.hz - 10.0f) / 990.0f, // 将10-1000的频率值转换为0.0-1.0的范围
                freqB = (bV3.hz - 10.0f) / 990.0f  // 将10-1000的频率值转换为0.0-1.0的范围
            )
        }

        override fun updateState(currentTime: Double) {
            // 可以在这里添加状态更新逻辑
        }
    }

    // 更新播放模式
    fun updatePlayMode(channel: String, mode: String, context: Context) {
        // 保存状态到state
        _state.update {
            when (channel) {
                "A" -> it.copy(aChannelPlayMode = mode)
                "B" -> it.copy(bChannelPlayMode = mode)
                else -> it
            }
        }

        // 保存状态
        saveState(context)
    }

    // 更新播放时间
    fun updatePlayTime(channel: String, time: String, context: Context) {
        // 保存状态到state
        _state.update {
            when (channel) {
                "A" -> it.copy(aChannelPlayTime = time)
                "B" -> it.copy(bChannelPlayTime = time)
                else -> it
            }
        }

        // 保存状态
        saveState(context)
    }

    // 更新已选择的波形列表
    fun updateSelectedWaves(channel: String, waves: List<WaveInfo>, context: Context) {
        // 获取当前通道的波形列表，用于对比删除的波形
        val currentState = _state.value
        val currentWaves = when (channel) {
            "A" -> currentState.aChannelSelectedWaves
            "B" -> currentState.bChannelSelectedWaves
            else -> emptyList()
        }

        // 计算被删除的波形
        val deletedWaves = currentWaves.filter { !waves.contains(it) }

        // 清除被删除波形的缓存
        deletedWaves.forEach { wave ->
            waveCache.remove(wave.path)
            ctrlItemCache.remove(wave.path)
        }

        _state.update {
            when (channel) {
                "A" -> it.copy(
                    aChannelSelectedWaves = waves,
                    aChannelPlayIndex = 0,
                    aChannelPlayElapsedTime = 0.0
                )

                "B" -> it.copy(
                    bChannelSelectedWaves = waves,
                    bChannelPlayIndex = 0,
                    bChannelPlayElapsedTime = 0.0
                )

                else -> it
            }
        }

        // 保存状态
        saveState(context)
    }

    // 解析播放时间字符串为秒数
    private fun parsePlayTime(playTime: String): Int {
        return playTime.replace("秒", "").replace("s", "").toIntOrNull() ?: 10
    }

    // 获取下一个播放索引
    private fun getNextPlayIndex(playMode: String, currentIndex: Int, listSize: Int): Int {
        if (listSize <= 1) return 0

        when (playMode) {
            "列表循环", "List Loop" -> {
                var tempIdx = currentIndex + 1
                if (tempIdx >= listSize) {
                    tempIdx = 0
                }
                return tempIdx
            }

            "单曲循环", "Single Loop" -> {
                return currentIndex
            }

            "随机", "Random" -> {
                val randomIndex = (Math.random() * listSize).toInt()
                return if (randomIndex == currentIndex && listSize > 1) {
                    (currentIndex + 1) % listSize
                } else {
                    randomIndex
                }
            }

            else -> {
                return 0
            }
        }
    }

    // 更新播放状态
    fun updatePlayState(
        channel: String,
        playIndex: Int,
        playElapsedTime: Double
    ) {
        _state.update {
            when (channel) {
                "A" -> it.copy(
                    aChannelPlayIndex = playIndex,
                    aChannelPlayElapsedTime = playElapsedTime
                )

                "B" -> it.copy(
                    bChannelPlayIndex = playIndex,
                    bChannelPlayElapsedTime = playElapsedTime
                )

                else -> it
            }
        }
    }

    // 更新播放时间（用于WavePulseSource同步时间）
    fun updateElapsedTime(aElapsedTime: Double, bElapsedTime: Double) {
        _state.update {
            it.copy(
                aChannelPlayElapsedTime = aElapsedTime,
                bChannelPlayElapsedTime = bElapsedTime
            )
        }
    }
}

// 波形信息数据类
data class WaveInfo(
    val name: String,
    val path: String
)

// 状态数据类
data class WaveState(
    val aChannelPlaying: Boolean = false,
    val bChannelPlaying: Boolean = false,
    val aChannelPlayMode: String = "列表循环",
    val bChannelPlayMode: String = "列表循环",
    val aChannelPlayTime: String = "10秒",
    val bChannelPlayTime: String = "10秒",
    val aChannelSelectedWaves: List<WaveInfo> = emptyList(),
    val bChannelSelectedWaves: List<WaveInfo> = emptyList(),
    // 播放索引
    val aChannelPlayIndex: Int = 0,
    val bChannelPlayIndex: Int = 0,
    // 播放时间（秒）
    val aChannelPlayElapsedTime: Double = 0.0,
    val bChannelPlayElapsedTime: Double = 0.0,
)


@Composable
fun WavePanel(viewModel: WaveViewModel) {
    val context = LocalContext.current

    // 初始化时从assets目录加载内置波形
    LaunchedEffect(Unit) {
        viewModel.loadBuiltInWavesFromAssets(context)
        // 加载保存的状态
        viewModel.loadSavedState(context)
    }

    // 使用Activity Result API处理文件夹选择结果
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 获取文件夹路径并加载波形
            val docId = android.provider.DocumentsContract.getTreeDocumentId(it)
            val path = android.provider.DocumentsContract.buildDocumentUriUsingTree(it, docId)
            viewModel.loadWavesFromFolderUri(context, it)
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = 600.dp) // 添加最小高度，确保打开脉冲图表后列表仍然可见
            .padding(4.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        // 顶部选择本地文件夹功能
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                folderPickerLauncher.launch(null)
            }) {
                Text(text = stringResource(R.string.select_local_folder))
            }
        }

        // 通道面板
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧区域 - 通道A
            WaveChannelPanel(
                channel = "A",
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1.2f)
                    .padding(end = 2.dp)
            )

            // 右侧区域 - 通道B
            WaveChannelPanel(
                channel = "B",
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1.2f)
                    .padding(start = 2.dp)
            )
        }
    }

    // 波形选择对话框
    WaveSelectionDialog(viewModel = viewModel)

    // 删除确认对话框
    DeleteConfirmDialog(viewModel = viewModel)
}

@Composable
fun WaveChannelPanel(channel: String, viewModel: WaveViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val channelName = if (channel == "A") stringResource(R.string.channel_a) else stringResource(R.string.channel_b)
    val isPlaying = if (channel == "A") state.aChannelPlaying else state.bChannelPlaying
    val playMode = if (channel == "A") state.aChannelPlayMode else state.bChannelPlayMode
    val playTime = if (channel == "A") state.aChannelPlayTime else state.bChannelPlayTime
    val selectedWaves = 
        if (channel == "A") state.aChannelSelectedWaves else state.bChannelSelectedWaves
    
    // 生成播放模式选项
    val playModeOptions = listOf(
        stringResource(R.string.play_mode_list_loop),
        stringResource(R.string.play_mode_single_loop),
        stringResource(R.string.play_mode_random)
    )
    
    // 生成播放时间选项
    val playTimeOptions = listOf(
        stringResource(R.string.play_time_5s),
        stringResource(R.string.play_time_10s),
        stringResource(R.string.play_time_30s),
        stringResource(R.string.play_time_60s),
        stringResource(R.string.play_time_120s)
    )

    Column(
        modifier = modifier
            .padding(4.dp) // 减少边框留白
            .border(1.dp, MaterialTheme.colorScheme.outline, shape = MaterialTheme.shapes.small)
            .padding(8.dp) // 减少内部留白
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第一行：通道名称和开始/停止按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = channelName, style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { viewModel.toggleChannelPlay(context, channel) },
                modifier = Modifier.width(80.dp)
            ) {
                Text(text = if (isPlaying) stringResource(R.string.stop) else stringResource(R.string.start))
            }
        }

        // 第二行：选择波形按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = { viewModel.showWaveSelectionDialog(channel) }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(R.drawable.plus), contentDescription = null)
                    Text(text = stringResource(R.string.select_wave))
                }
            }
        }

        // 播放模式和播放时间（同一行）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 播放模式
            var modeExpanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Button(
                    onClick = { modeExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(text = playMode, maxLines = 1, softWrap = false)
                }
                DropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    playModeOptions.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(text = mode) },
                            onClick = {
                                viewModel.updatePlayMode(channel, mode, context)
                                modeExpanded = false
                            }
                        )
                    }
                }
            }

            // 播放时间
            var timeExpanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Button(
                    onClick = { timeExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(text = playTime, maxLines = 1, softWrap = false)
                }
                DropdownMenu(
                    expanded = timeExpanded,
                    onDismissRequest = { timeExpanded = false }
                ) {
                    playTimeOptions.forEach { time ->
                        DropdownMenuItem(
                            text = { Text(text = time) },
                            onClick = {
                                viewModel.updatePlayTime(channel, time, context)
                                timeExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 已选择的波形列表
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape = MaterialTheme.shapes.small)
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            if (selectedWaves.isEmpty()) {
                Text(text = "No waves selected", style = MaterialTheme.typography.bodyMedium)
            } else {
                selectedWaves.forEachIndexed { index, wave ->
                    val isPlaying = if (channel == "A") {
                        state.aChannelPlaying && index == state.aChannelPlayIndex
                    } else {
                        state.bChannelPlaying && index == state.bChannelPlayIndex
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isPlaying) {
                                    Modifier.background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .combinedClickable(
                                onClick = { },
                                onDoubleClick = {
                                    // 双击切换到该波形并播放
                                    viewModel.switchToWave(context, channel, index)
                                }
                            )
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = wave.name,
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Icon(
                            painter = painterResource(R.drawable.bin),
                            contentDescription = null,
                            tint = if (isPlaying) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.clickable {
                                // 从已选择列表中删除波形
                                val channelIsPlaying = if (channel == "A") {
                                    state.aChannelPlaying
                                } else {
                                    state.bChannelPlaying
                                }

                                if (channelIsPlaying) {
                                    // 通道正在播放，显示确认对话框
                                    viewModel.showDeleteConfirmDialog(channel, wave)
                                } else {
                                    // 通道未播放，直接删除
                                    val updatedWaves = selectedWaves.filter { it != wave }
                                    when (channel) {
                                        "A" -> viewModel.updateSelectedWaves(
                                            "A",
                                            updatedWaves,
                                            context
                                        )

                                        "B" -> viewModel.updateSelectedWaves(
                                            "B",
                                            updatedWaves,
                                            context
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveSelectionDialog(viewModel: WaveViewModel) {
    val context = LocalContext.current
    val showDialog by viewModel.showWaveSelectionDialog.collectAsState()
    val availableWaves by viewModel.availableWaves.collectAsState()
    val currentChannel by viewModel.currentChannelForSelection.collectAsState()
    val state by viewModel.state.collectAsState()
    val selectedWaves =
        if (currentChannel == "A") state.aChannelSelectedWaves else state.bChannelSelectedWaves

    // 选中的波形ID列表
    val selectedWaveNames = selectedWaves.map { it.name }.toSet()
    val (selectedItems, setSelectedItems) = remember(currentChannel) {
        mutableStateOf(selectedWaveNames)
    }

    // 当selectedWaves变化时，同步更新selectedItems
    LaunchedEffect(selectedWaves) {
        setSelectedItems(selectedWaveNames)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWaveSelectionDialog() },
            title = { Text(text = "Select Wave") },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(scrollState)
                ) {
                    availableWaves.forEach { wave ->
                        val isSelected = selectedItems.contains(wave.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable {
                                    if (isSelected) {
                                        setSelectedItems(selectedItems - wave.name)
                                    } else {
                                        setSelectedItems(selectedItems + wave.name)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (it) {
                                        setSelectedItems(selectedItems + wave.name)
                                    } else {
                                        setSelectedItems(selectedItems - wave.name)
                                    }
                                }
                            )
                            Text(text = wave.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val selectedWaveInfos =
                        availableWaves.filter { selectedItems.contains(it.name) }
                    viewModel.selectWaves(selectedWaveInfos, context)
                }) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.dismissWaveSelectionDialog() }) {
                    Text(text = "取消")
                }
            }
        )
    }
}

@Composable
fun DeleteConfirmDialog(viewModel: WaveViewModel) {
    val context = LocalContext.current
    val showDialog by viewModel.showDeleteConfirmDialog.collectAsState()
    val waveToDelete by viewModel.waveToDelete.collectAsState()
    val channelForDelete by viewModel.channelForDelete.collectAsState()

    if (showDialog && waveToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmDialog() },
            title = { Text(text = "Confirm Delete") },
            text = { Text(text = "Channel ${channelForDelete} is playing, do you want to stop playing and delete the wave ${waveToDelete!!.name}?") },
            confirmButton = {
                Button(onClick = { viewModel.confirmDeleteWave(context) }) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.dismissDeleteConfirmDialog() }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

data class V2Model(
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0
)

data class V3Model(
    var hz: Int = 0,
    var z: Int = 0
)

data class WaveMeta(
    var y: Int = 100,
    var anchor: Int = 0
)

/**
 * 波形小节数据类
 * 用于表示波形文件中的一个小节
 */
class WaveStage {
    /**
     * 是否启用小节 小节状态 0-禁用 1-启用
     */
    var enabled: Boolean = false

    /**
     * 频率类型 1-固定 2-节间渐变 3-元内渐变 4-元间渐变
     */
    var hzType: Int = 1

    /**
     * 渐变类型 0-从小变大 1-从大变小
     */
    var hzGradient: Int = 0

    var hzMin: Int = 0
    var hzMax: Int = 0

    /**
     * 频率数组
     */
    private var hz: List<Int>? = null

    /**
     * 小节循环次数
     */
    private var times: Int = 0

    /**
     * 小节时长(滑块)
     */
    private var stageTime: Int = 0

    /**
     * 小节总时长(完整播放小节所消耗时间)
     */
    private var stageTotalTime: Long = 0

    /**
     * 脉冲元列表
     */
    val metas: MutableList<WaveMeta> = mutableListOf()

    /**
     * 获取频率数组
     */
    fun getHz(): List<Int>? {
        if (hz == null) {
            if (hzMin != 0 && hzMax != 0) {
                hz = listOf(hzMin, hzMax)
            }
        }
        return hz
    }

    /**
     * 设置频率数组
     */
    fun setHz(hz: List<Int>?) {
        this.hz = hz
    }

    /**
     * 设置小节时长
     */
    fun setStageTime(stageTime: Int) {
        this.stageTime = stageTime
    }

    /**
     * 获取小节时长
     */
    fun getStageTime(): Int {
        return stageTime
    }

    /**
     * 设置小节循环次数
     */
    fun setTimes(times: Int) {
        this.times = times
    }

    /**
     * 获取小节总时长
     */
    fun getStageTotalTime(): Long {
        val t = getTimes()
        // 脉冲元 * 窗口时间 = 小节一次脉冲时间
        val metaTime = metas.size * WaveUtil.WINDOW_TIME_DOUBLE
        stageTotalTime = t * metaTime.toLong()
        return stageTotalTime
    }

    /**
     * 获取小节循环次数
     */
    fun getTimes(): Int {
        if (enabled && metas.isNotEmpty()) {
            // 这是官方给定的转换公式 小节时长转毫秒公式
            val stageTime = WaveUtil.toStageTime(this.stageTime)
            // 脉冲元 * 窗口时间 = 小节一次脉冲时间
            val metaTime = metas.size * WaveUtil.WINDOW_TIME_DOUBLE
            // 上取整 就是小节循环次数
            times = kotlin.math.ceil(stageTime.toDouble() / metaTime).toInt()
            if (times <= 0) {
                times = 1
            }
        }
        return times
    }
}

class CtrlItem {
    var id: String? = null

    /** 是否双通道 */
    var doubleChannel: Boolean = false

    /**
     * 创建时间
     */
    var createDate: Date = Date()

    /**
     * 修改时间
     */
    var updateDate: Date = Date()

    /**
     * 组件名称
     */
    var name: String = ""

    /**
     * 持续总时间 (毫秒)（波形完整播放一次的时间）
     */
    private var duration: Long = 0

    /**
     * 播放速率 x1 x2 x4
     */
    var rate: Int = 1

    /**
     * 休息时长
     */
    var restTime: Int = 0

    /**
     * 最小播放时长(如果该值比duration大，则会按照时间补全波形输出)
     */
    var minRuration: Long = 0

    /**
     * 节点列表
     */
    private val stageList: MutableList<WaveStage> = mutableListOf()

    /**
     * 通道A节点列表
     */
    val stageA: MutableList<WaveStage> = mutableListOf()

    /**
     * 通道B节点列表
     */
    val stageB: MutableList<WaveStage> = mutableListOf()

    /**
     * V3脉冲元节点列表
     */
    var v3ModelList: List<V3Model>? = null

    /**
     * 获取持续总时间
     * 每次调用都会重新计算
     * @return 持续总时间(毫秒)
     */
    fun getDuration(): Long {
        val stages = getStageList()
        if (stages.isNotEmpty()) {
            var dur = 0L
            for (stage in stages) {
                if (stage.enabled) {
                    dur += stage.getStageTotalTime()
                }
            }
            // 单位毫秒
            duration = dur
        }
        return duration
    }

    /**
     * 获取V3模型列表
     * 每次调用都会重新计算
     * @return V3模型列表
     */
    fun getV3ModelList(): Any {
        return if (doubleChannel) {
            mapOf(
                "a" to getV3ModelListByChannel(stageA),
                "b" to getV3ModelListByChannel(stageB)
            )
        } else {
            if (stageList.isNotEmpty()) {
                v3ModelList = getV3ModelListByChannel(stageList)
            }
            v3ModelList ?: emptyList<V3Model>()
        }
    }

    fun getV3ModelListByChannel(stList: List<WaveStage>): List<V3Model> {
        val v2ModelList = mutableListOf<V2Model>()
        for (stage in stList) {
            // 启用了小节就计算V2Model
            if (stage.enabled) {

                val stageList1 = WaveUtil.stageV2Exchange(stage)
                v2ModelList.addAll(stageList1)
            }
        }
        if (restTime > 0) {
            // 除10 计算休息时长需要循环几次 每次100毫秒
            val restTemp = restTime / 10
            for (i in 0 until restTemp) {
                val v2Model = V2Model(5, 95, 0)
                v2ModelList.add(v2Model)
            }
        }
        return WaveUtil.v2ToV3(v2ModelList)
    }

    /**
     * 获取总时间（包含休息时间）
     * 每次调用都会重新计算
     * @return 总时间(毫秒)
     */
    fun getTotalTime(): Long {
        // 每次都重新计算
        val totalTime = getDuration()
        // 0 - 100
        val restTime = this.restTime
        // 除10后向上取整 * 100变为毫秒
        val restMs = kotlin.math.ceil(restTime.toDouble() / 10).toLong() * 100
        return totalTime + restMs
    }

    /**
     * 获取总时间的字符串表示
     * @return 格式化的时间字符串
     */
    fun getTotalTimeStr(): String {
        return WaveUtil.msToViewTimeStr(getTotalTime())
    }


    /**
     * 获取节点列表
     * @return 节点列表
     */
    fun getStageList(): List<WaveStage> {
        return stageList.toList()
    }

    /**
     * 设置节点列表
     * @param stageList 要设置的节点列表
     */
    fun setStageList(stageList: List<WaveStage>) {
        this.stageList.clear()
        this.stageList.addAll(stageList)
    }

    /**
     * 格式化日期为指定格式
     * @param date 要格式化的日期
     * @return 格式化后的日期字符串 (yyyy/MM/dd HH:mm:ss)
     */
    fun formatDate(date: Date): String {
        val year = date.year + 1900
        val month = String.format("%02d", date.month + 1)
        val day = String.format("%02d", date.date)
        val hours = String.format("%02d", date.hours)
        val minutes = String.format("%02d", date.minutes)
        val seconds = String.format("%02d", date.seconds)
        return "$year/$month/$day $hours:$minutes:$seconds"
    }

    /**
     * 获取格式化的创建时间
     * @return 格式化的创建时间字符串
     */
    fun getFormattedCreateDate(): String {
        return formatDate(createDate)
    }

    /**
     * 获取格式化的修改时间
     * @return 格式化的修改时间字符串
     */
    fun getFormattedUpdateDate(): String {
        return formatDate(updateDate)
    }

    /**
     * 克隆当前对象
     * @return 克隆的对象
     */
    fun clone(): CtrlItem {
        val cloned = CtrlItem()
        cloned.id = this.id
        cloned.doubleChannel = this.doubleChannel
        cloned.createDate = Date(this.createDate.time)
        cloned.updateDate = Date(this.updateDate.time)
        cloned.name = this.name
        cloned.duration = this.duration
        cloned.rate = this.rate
        cloned.restTime = this.restTime
        cloned.minRuration = this.minRuration
        cloned.stageList.addAll(this.stageList)
        cloned.stageA.addAll(this.stageA)
        cloned.stageB.addAll(this.stageB)
        cloned.v3ModelList = this.v3ModelList?.toList()
        return cloned
    }
}