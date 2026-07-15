package com.pocket48.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocket48.app.Pocket48App
import com.pocket48.app.data.model.MemberInfo
import com.pocket48.app.data.model.MemberStatus
import com.pocket48.app.data.model.effectiveStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 主要团体缩写映射 */
private val MAIN_GROUP_MAP = linkedMapOf(
    "SNH48" to "SNH",
    "GNZ48" to "GNZ",
    "BEJ48" to "BEJ",
    "CKG48" to "CKG",
    "CGT48" to "CGT",
    "SHY48" to "SHY",
)

/** 保留顺序的主要缩写列表 */
private val MAIN_ABBR_LIST = MAIN_GROUP_MAP.values.toList()

/** 缩写 → 全称 */
private val ABBR_TO_FULL = MAIN_GROUP_MAP.entries.associate { (k, v) -> v to k }

/**
 * 队伍排序顺序 - 与官方 SNH 内部排序一致
 * 同一团体下按登场年份/队伍序号排序
 */
private val TEAM_ORDER = listOf(
    // SNH48
    "TEAM SII", "TEAM NII", "TEAM HII", "TEAM X", "预备生",
    "TEAM XII", "TEAM SIII", "TEAM HIII", "TEAM FT",
    // GNZ48
    "TEAM G", "TEAM NIII", "TEAM Z", "GNZ预备生",
    // BEJ48
    "TEAM B", "TEAM E", "TEAM J", "BEJ预备生",
    // CKG48
    "TEAM C", "TEAM K", "CKG预备生",
    // CGT48
    "TEAM CII", "TEAM GII", "CGT预备生",
    // SHY48
    "SHY48",
    // 特殊
    "IDFT", "团魂", "燃烧吧团魂", "海外练习生", "新星闪耀计划", "丝芭影视",
    "荣誉毕业生", "明星殿堂", "未入列",
)

private fun teamSortIndex(name: String): Int =
    TEAM_ORDER.indexOf(name).let { if (it >= 0) it else TEAM_ORDER.size }

/** 一级筛选项 */
enum class Level1Mode { ACTIVE, ALL, FAVORITES }

class MemberViewModel : ViewModel() {

    private val store = Pocket48App.instance.memberStore

    private val _allMembers = MutableStateFlow<List<MemberInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _level1Mode = MutableStateFlow(Level1Mode.ACTIVE)   // 默认现役
    private val _selectedAbbr = MutableStateFlow("")                // 选中的缩写
    private val _selectedTeam = MutableStateFlow("")                // 选中的队伍
    private val _groupsExpanded = MutableStateFlow(false)           // 二级展开
    private val _isLoading = MutableStateFlow(true)

    val isLoading = _isLoading.asStateFlow()
    val searchQuery = _searchQuery.asStateFlow()
    val level1Mode = _level1Mode.asStateFlow()
    val selectedAbbr = _selectedAbbr.asStateFlow()
    val selectedTeam = _selectedTeam.asStateFlow()
    val groupsExpanded = _groupsExpanded.asStateFlow()

    val favoriteIds: StateFlow<Set<Long>> =
        store.favoriteIds.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    /** 组合筛选参数 */
    private data class FilterState(
        val query: String = "",
        val status: MemberStatus? = MemberStatus.ACTIVE,
        val group: String = "",
        val team: String = "",
        val favOnly: Boolean = false,
        val favs: Set<Long> = emptySet(),
    )

    private val filterState: StateFlow<FilterState> = combine(
        _searchQuery, _level1Mode, _selectedAbbr, _selectedTeam,
        combine(_allMembers, favoriteIds) { _, f -> f },
    ) { q, mode, abbr, team, favs ->
        val status = when (mode) {
            Level1Mode.ACTIVE -> MemberStatus.ACTIVE
            Level1Mode.ALL -> null
            Level1Mode.FAVORITES -> null
        }
        val group = ABBR_TO_FULL[abbr] ?: ""
        val favOnly = mode == Level1Mode.FAVORITES
        FilterState(q, status, group, team, favOnly, favs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, FilterState())

    /** 筛选后的成员列表（默认按团队 → 队伍 → 拼音排序） */
    val filteredMembers: StateFlow<List<MemberInfo>> = combine(
        _allMembers, filterState
    ) { members, state ->
        members.filter { m ->
            val st = m.effectiveStatus()
            (state.status == null || st == state.status) &&
            (state.group.isEmpty() || m.groupName == state.group) &&
            (state.team.isEmpty() || m.teamName == state.team) &&
            (!state.favOnly || m.userId in state.favs) &&
            (state.query.isEmpty() ||
                m.nickname.contains(state.query, ignoreCase = true) ||
                m.realName.contains(state.query, ignoreCase = true) ||
                m.pinyin.contains(state.query, ignoreCase = true) ||
                m.abbr.contains(state.query, ignoreCase = true))
        }.sortedWith(
            // 收藏置顶 → 状态(在籍优先) → 团体顺序 → 队伍顺序 → 拼音
            compareByDescending<MemberInfo> { it.userId in state.favs }
                .thenBy { it.displayStatus.ordinal }
                .thenBy { MAIN_GROUP_MAP[it.groupName]?.let { abbr -> MAIN_ABBR_LIST.indexOf(abbr) } ?: 999 }
                .thenBy { teamSortIndex(it.teamName) }
                .thenBy { it.pinyin }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 按队伍分组成员（用于队伍 section header 视图） */
    data class MemberGroup(val teamName: String, val members: List<MemberInfo>)

    val groupedMembers: StateFlow<List<MemberGroup>> = filteredMembers
        .map { list ->
            list.groupBy { it.teamName.ifBlank { "未入列" } }
                .toSortedMap(compareBy<String> { teamSortIndex(it) }.thenBy { it })
                .map { (team, ms) -> MemberGroup(team, ms) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 主要团体缩写列表 (保留顺序) */
    val mainAbbrs: List<String> get() = MAIN_ABBR_LIST

    /** 在当前状态/搜索/关注条件下，成员数 > 0 的主要团体缩写 */
    val availableAbbrs: StateFlow<List<String>> = combine(
        _allMembers, _level1Mode, _searchQuery, favoriteIds
    ) { members, mode, query, favs ->
        val base = applyNonGroupFilter(members, mode, query, favs)
        MAIN_GROUP_MAP.keys
            .filter { g -> base.any { it.groupName == g } }
            .mapNotNull { MAIN_GROUP_MAP[it] }
    }.stateIn(viewModelScope, SharingStarted.Lazily, MAIN_ABBR_LIST)

    /** 在当前状态/搜索/关注条件下，成员数 > 0 的队伍 */
    val availableTeams: StateFlow<List<String>> = combine(
        _allMembers, _level1Mode, _selectedAbbr, _searchQuery, favoriteIds
    ) { members, mode, abbr, query, favs ->
        val full = ABBR_TO_FULL[abbr] ?: return@combine emptyList()
        val base = applyNonGroupFilter(members, mode, query, favs)
            .filter { it.groupName == full }
        base.map { it.teamName }.filter { it.isNotEmpty() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 是否有任何收藏（用于决定是否显示"关注"Tab） */
    val hasFavorites: StateFlow<Boolean> =
        favoriteIds.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    /** 应用非团体/队伍维度的过滤（状态/搜索/关注），用于计算可用筛选标签 */
    private fun applyNonGroupFilter(
        members: List<MemberInfo>,
        mode: Level1Mode,
        query: String,
        favs: Set<Long>,
    ): List<MemberInfo> = members.filter { m ->
        val st = m.effectiveStatus()
        val statusOk = when (mode) {
            Level1Mode.ACTIVE -> st == MemberStatus.ACTIVE
            Level1Mode.ALL -> true
            Level1Mode.FAVORITES -> m.userId in favs
        }
        val queryOk = query.isEmpty() ||
            m.nickname.contains(query, ignoreCase = true) ||
            m.realName.contains(query, ignoreCase = true) ||
            m.pinyin.contains(query, ignoreCase = true) ||
            m.abbr.contains(query, ignoreCase = true)
        statusOk && queryOk
    }

    /** 清空所有筛选（仅切回 ACTIVE，不改搜索） */
    fun clearFilters() {
        _level1Mode.value = Level1Mode.ACTIVE
        _selectedAbbr.value = ""
        _selectedTeam.value = ""
        _groupsExpanded.value = false
    }

    init { loadMembers() }

    fun loadMembers() {
        _isLoading.value = true
        _allMembers.value = store.loadMembers()
        _isLoading.value = false
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** 选择一级菜单 */
    fun selectLevel1(mode: Level1Mode) {
        if (_level1Mode.value == mode) return // 相同则不操作
        _level1Mode.value = mode
        _selectedAbbr.value = ""
        _selectedTeam.value = ""
        _groupsExpanded.value = false
    }

    /** 展开/收起二级（团体） */
    fun toggleGroups() {
        _groupsExpanded.value = !_groupsExpanded.value
        if (!_groupsExpanded.value) {
            _selectedAbbr.value = ""
            _selectedTeam.value = ""
        }
    }

    /** 选择缩写 - 选中后队伍自动展开 */
    fun selectAbbr(abbr: String) {
        _selectedAbbr.value = if (_selectedAbbr.value == abbr) "" else abbr
        _selectedTeam.value = ""
    }

    /** 选择队伍 */
    fun selectTeam(team: String) {
        _selectedTeam.value = if (_selectedTeam.value == team) "" else team
    }

    /** 查看所有收藏 */
    fun showFavorites() { selectLevel1(Level1Mode.FAVORITES) }

    fun toggleFavorite(userId: Long) {
        viewModelScope.launch { store.toggleFavorite(userId) }
    }
}
