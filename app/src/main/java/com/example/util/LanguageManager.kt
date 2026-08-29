package com.example.util

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

enum class AppLanguage(
    val englishName: String,
    val nativeName: String,
    val flagEmoji: String
) {
    EN("English", "English", "🇺🇸"),
    ES("Spanish", "Español", "🇪🇸"),
    FR("French", "Français", "🇫🇷"),
    DE("German", "Deutsch", "🇩🇪"),
    PT("Portuguese", "Português", "🇧🇷"),
    IT("Italian", "Italiano", "🇮🇹"),
    RU("Russian", "Русский", "🇷🇺"),
    JA("Japanese", "日本語", "🇯🇵"),
    KO("Korean", "한국어", "🇰🇷"),
    ZH("Chinese", "简体中文", "🇨🇳"),
    HI("Hindi", "हिन्दी", "🇮🇳"),
    AR("Arabic", "العربية", "🇸🇦");

    val displayName: String get() = "$flagEmoji $nativeName"
}

data class AppStrings(
    // Bottom Navigation
    val navHome: String = "Home",
    val navSubscriptions: String = "Subscriptions",
    val navLibrary: String = "Library",
    val navShorts: String = "Shorts",
    val navSearch: String = "Search",

    // Library Tabs
    val tabSubjects: String = "Subjects",
    val tabDownloads: String = "Downloads",
    val tabFavorites: String = "Favorites",
    val tabWatchLater: String = "Watch Later",
    val tabHistory: String = "History",

    // Library Content
    val libraryTitle: String = "Library & Downloads",
    val customPlaylists: String = "Custom Playlists",
    val addCategory: String = "Add Category",
    val noFavoritesText: String = "No favorite videos saved yet. Tap the star icon on any video to bookmark it here!",
    val noWatchLaterText: String = "Your Watch Later queue is empty. Add videos from the home feed to save them for later!",
    val noHistoryText: String = "No watch history recorded yet. Videos you watch will automatically appear here!",
    val noDownloadsText: String = "No downloaded videos yet. Tap the download icon while playing any video for offline viewing.",

    // Top Category Filters
    val catAll: String = "All",
    val catLast24h: String = "⏰ Last 24h",
    val catTechCode: String = "Tech & Code",
    val catMusic: String = "Music",
    val catTutorials: String = "Tutorials",
    val catGaming: String = "Gaming",
    val catFocusAmbient: String = "Focus & Ambient",

    // Video Player Actions
    val btnAiSummary: String = "AI Summary",
    val btnDownload: String = "Download",
    val btnDownloaded: String = "Downloaded",
    val btnShare: String = "Share",
    val btnLike: String = "Like",
    val btnLiked: String = "Liked",
    val btnDislike: String = "Dislike",
    val btnDisliked: String = "Disliked",
    val sponsorSkipped: String = "Skipped Sponsor",

    // Settings & Dialogs
    val settingsTitle: String = "Settings & Algorithm",
    val appLanguageTitle: String = "App Language",
    val appLanguageSub: String = "Select your preferred display language for instant UI update",
    val adBlockActive: String = "AdBlock Active",
    val adBlockSub: String = "Commercial YouTube ads suppressed during playback.",
    val advertsAllowed: String = "Adverts Allowed",
    val advertsAllowedSub: String = "Standard YouTube ads play.",
    val closeBtn: String = "Close",
    val cancelBtn: String = "Cancel",
    val deleteBtn: String = "Delete",

    // Try Our Other Products
    val tryOurOtherProducts: String = "Try Our Other Products ✨",
    val otherProductsSub: String = "Discover more powerful tools created by our team:",
    val visitWebsite: String = "Visit Website 🌐",
    val getOnStore: String = "Get on Store 🛒"
)

object LanguageManager {
    private const val PREFS_NAME = "vixz_language_prefs"
    private const val KEY_LANGUAGE = "selected_app_language"

    private val _currentLanguage = MutableStateFlow(AppLanguage.EN)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val translations = mapOf(
        AppLanguage.EN to AppStrings(),
        AppLanguage.ES to AppStrings(
            navHome = "Inicio",
            navSubscriptions = "Suscripciones",
            navLibrary = "Biblioteca",
            navShorts = "Shorts",
            navSearch = "Buscar",
            tabSubjects = "Temas",
            tabDownloads = "Descargas",
            tabFavorites = "Favoritos",
            tabWatchLater = "Ver Más Tarde",
            tabHistory = "Historial",
            libraryTitle = "Biblioteca y Descargas",
            customPlaylists = "Listas Personalizadas",
            addCategory = "Añadir Categoría",
            noFavoritesText = "Aún no hay videos favoritos. ¡Toca la estrella en cualquier video para guardarlo aquí!",
            noWatchLaterText = "Tu lista de Ver Más Tarde está vacía. ¡Añade videos desde el inicio para verlos luego!",
            noHistoryText = "Aún no hay historial de reproducción. ¡Los videos que veas aparecerán aquí!",
            noDownloadsText = "No hay videos descargados. Toca el botón de descarga en el reproductor para ver sin conexión.",
            catAll = "Todos",
            catLast24h = "⏰ Últimas 24h",
            catTechCode = "Tecnología y Código",
            catMusic = "Música",
            catTutorials = "Tutoriales",
            catGaming = "Videojuegos",
            catFocusAmbient = "Enfoque y Ambiente",
            btnAiSummary = "Resumen IA",
            btnDownload = "Descargar",
            btnDownloaded = "Descargado",
            btnShare = "Compartir",
            btnLike = "Me gusta",
            btnLiked = "Te gustó",
            btnDislike = "No me gusta",
            btnDisliked = "Ocultado",
            sponsorSkipped = "Patrocinio Omitido",
            settingsTitle = "Ajustes y Algoritmo",
            appLanguageTitle = "Idioma de la Aplicación",
            appLanguageSub = "Selecciona tu idioma preferido para actualización instantánea",
            adBlockActive = "AdBlock Activo",
            adBlockSub = "Anuncios comerciales de YouTube bloqueados.",
            advertsAllowed = "Anuncios Permitidos",
            advertsAllowedSub = "Se reproducen anuncios normales.",
            closeBtn = "Cerrar",
            cancelBtn = "Cancelar",
            deleteBtn = "Eliminar",
            tryOurOtherProducts = "Prueba Nuestros Otros Productos ✨",
            otherProductsSub = "Descubre más herramientas desarrolladas por nuestro equipo:",
            visitWebsite = "Visitar Sitio Web 🌐",
            getOnStore = "Obtener en Tienda 🛒"
        ),
        AppLanguage.FR to AppStrings(
            navHome = "Accueil",
            navSubscriptions = "Abonnements",
            navLibrary = "Bibliothèque",
            navShorts = "Shorts",
            navSearch = "Rechercher",
            tabSubjects = "Sujets",
            tabDownloads = "Téléchargements",
            tabFavorites = "Favoris",
            tabWatchLater = "À regarder plus tard",
            tabHistory = "Historique",
            libraryTitle = "Bibliothèque et Téléchargements",
            customPlaylists = "Playlists Personnalisées",
            addCategory = "Ajouter Catégorie",
            noFavoritesText = "Aucune vidéo favorite. Touchez l'étoile sur une vidéo pour l'ajouter ici!",
            noWatchLaterText = "Votre liste À regarder plus tard est vide.",
            noHistoryText = "Aucun historique pour le moment. Les vidéos visionnées apparaîtront ici.",
            noDownloadsText = "Aucun téléchargement. Téléchargez des vidéos pour les regarder hors ligne.",
            catAll = "Tout",
            catLast24h = "⏰ Dernières 24h",
            catTechCode = "Tech et Code",
            catMusic = "Musique",
            catTutorials = "Tutoriels",
            catGaming = "Jeux Vidéo",
            catFocusAmbient = "Focus et Ambiance",
            btnAiSummary = "Résumé IA",
            btnDownload = "Télécharger",
            btnDownloaded = "Téléchargé",
            btnShare = "Partager",
            btnLike = "J'aime",
            btnLiked = "Aimé",
            btnDislike = "Je n'aime pas",
            btnDisliked = "Masqué",
            sponsorSkipped = "Sponsor Ignoré",
            settingsTitle = "Paramètres et Algorithme",
            appLanguageTitle = "Langue de l'Application",
            appLanguageSub = "Choisissez votre langue pour une mise à jour instantanée de l'interface",
            adBlockActive = "AdBlock Actif",
            adBlockSub = "Publicités commerciales YouTube bloquées.",
            advertsAllowed = "Publicités Autorisées",
            advertsAllowedSub = "Les publicités normales sont lues.",
            closeBtn = "Fermer",
            cancelBtn = "Annuler",
            deleteBtn = "Supprimer",
            tryOurOtherProducts = "Essayez Nos Autres Produits ✨",
            otherProductsSub = "Découvrez d'autres outils créés par notre équipe :",
            visitWebsite = "Visiter le Site 🌐",
            getOnStore = "Voir sur le Store 🛒"
        ),
        AppLanguage.DE to AppStrings(
            navHome = "Startseite",
            navSubscriptions = "Abos",
            navLibrary = "Mediathek",
            navShorts = "Shorts",
            navSearch = "Suchen",
            tabSubjects = "Themen",
            tabDownloads = "Downloads",
            tabFavorites = "Favoriten",
            tabWatchLater = "Später ansehen",
            tabHistory = "Verlauf",
            libraryTitle = "Mediathek & Downloads",
            customPlaylists = "Eigene Playlists",
            addCategory = "Kategorie hinzufügen",
            noFavoritesText = "Noch keine Favoriten gespeichert. Tippe auf den Stern bei einem Video!",
            noWatchLaterText = "Deine 'Später ansehen'-Liste ist leer.",
            noHistoryText = "Noch kein Verlauf vorhanden. Angesehene Videos erscheinen hier.",
            noDownloadsText = "Keine Downloads vorhanden. Lade Videos für die Offline-Wiedergabe herunter.",
            catAll = "Alle",
            catLast24h = "⏰ Letzte 24 Std.",
            catTechCode = "Tech & Programmierung",
            catMusic = "Musik",
            catTutorials = "Tutorials",
            catGaming = "Gaming",
            catFocusAmbient = "Fokus & Ambient",
            btnAiSummary = "KI-Zusammenfassung",
            btnDownload = "Herunterladen",
            btnDownloaded = "Heruntergeladen",
            btnShare = "Teilen",
            btnLike = "Gefällt mir",
            btnLiked = "Gefällt mir",
            btnDislike = "Gefällt mir nicht",
            btnDisliked = "Ausgeblendet",
            sponsorSkipped = "Sponsor übersprungen",
            settingsTitle = "Einstellungen & Algorithmus",
            appLanguageTitle = "App-Sprache",
            appLanguageSub = "Wähle deine bevorzugte Anzeigesprache",
            adBlockActive = "AdBlock Aktiv",
            adBlockSub = "Kommerzielle YouTube-Werbung wird blockiert.",
            advertsAllowed = "Werbung Erlaubt",
            advertsAllowedSub = "Standardmäßige Werbung wird abgespielt.",
            closeBtn = "Schließen",
            cancelBtn = "Abbrechen",
            deleteBtn = "Löschen",
            tryOurOtherProducts = "Entdecke Unsere Weiteren Produkte ✨",
            otherProductsSub = "Erfahre mehr über unsere weiteren Tools:",
            visitWebsite = "Website besuchen 🌐",
            getOnStore = "Im Store ansehen 🛒"
        ),
        AppLanguage.PT to AppStrings(
            navHome = "Início",
            navSubscriptions = "Inscrições",
            navLibrary = "Biblioteca",
            navShorts = "Shorts",
            navSearch = "Pesquisar",
            tabSubjects = "Assuntos",
            tabDownloads = "Downloads",
            tabFavorites = "Favoritos",
            tabWatchLater = "Assistir Mais Tarde",
            tabHistory = "Histórico",
            libraryTitle = "Biblioteca e Downloads",
            customPlaylists = "Playlists Personalizadas",
            addCategory = "Adicionar Categoria",
            noFavoritesText = "Nenhum favorito salvo ainda. Toque na estrela em qualquer vídeo para salvar!",
            noWatchLaterText = "Sua lista Assistir Mais Tarde está vazia.",
            noHistoryText = "Nenhum histórico gravado ainda.",
            noDownloadsText = "Nenhum vídeo baixado. Baixe vídeos para assistir offline.",
            catAll = "Todos",
            catLast24h = "⏰ Últimas 24h",
            catTechCode = "Tecnologia e Código",
            catMusic = "Música",
            catTutorials = "Tutoriais",
            catGaming = "Jogos",
            catFocusAmbient = "Foco e Ambiente",
            btnAiSummary = "Resumo IA",
            btnDownload = "Baixar",
            btnDownloaded = "Baixado",
            btnShare = "Compartilhar",
            btnLike = "Gostei",
            btnLiked = "Gostou",
            btnDislike = "Não gostei",
            btnDisliked = "Ocultado",
            sponsorSkipped = "Patrocínio Ignorado",
            settingsTitle = "Configurações e Algoritmo",
            appLanguageTitle = "Idioma do Aplicativo",
            appLanguageSub = "Selecione seu idioma preferido para atualização instantânea",
            adBlockActive = "AdBlock Ativo",
            adBlockSub = "Anúncios comerciais do YouTube bloqueados.",
            advertsAllowed = "Anúncios Permitidos",
            advertsAllowedSub = "Anúncios normais são exibidos.",
            closeBtn = "Fechar",
            cancelBtn = "Cancelar",
            deleteBtn = "Excluir",
            tryOurOtherProducts = "Experimente Nossos Outros Produtos ✨",
            otherProductsSub = "Descubra mais ferramentas incríveis desenvolvidas pela nossa equipe:",
            visitWebsite = "Visitar Site 🌐",
            getOnStore = "Obter na Loja 🛒"
        ),
        AppLanguage.IT to AppStrings(
            navHome = "Home",
            navSubscriptions = "Iscrizioni",
            navLibrary = "Raccolta",
            navShorts = "Shorts",
            navSearch = "Cerca",
            tabSubjects = "Argomenti",
            tabDownloads = "Download",
            tabFavorites = "Preferiti",
            tabWatchLater = "Guarda Più Tardi",
            tabHistory = "Cronologia",
            libraryTitle = "Raccolta e Download",
            customPlaylists = "Playlist Personalizzate",
            addCategory = "Aggiungi Categoria",
            noFavoritesText = "Nessun video preferito salvato. Tocca la stella su un video per salvarlo!",
            noWatchLaterText = "La tua lista Guarda Più Tardi è vuota.",
            noHistoryText = "Nessuna cronologia registrata finora.",
            noDownloadsText = "Nessun download. Scarica video per guardarli offline.",
            catAll = "Tutti",
            catLast24h = "⏰ Ultime 24h",
            catTechCode = "Tech e Codice",
            catMusic = "Musica",
            catTutorials = "Tutorial",
            catGaming = "Gaming",
            catFocusAmbient = "Focus e Relax",
            btnAiSummary = "Sommario IA",
            btnDownload = "Scarica",
            btnDownloaded = "Scaricato",
            btnShare = "Condividi",
            btnLike = "Mi piace",
            btnLiked = "Ti piace",
            btnDislike = "Non mi piace",
            btnDisliked = "Nascosto",
            sponsorSkipped = "Sponsor Saltato",
            settingsTitle = "Impostazioni e Algoritmo",
            appLanguageTitle = "Lingua dell'Applicazione",
            appLanguageSub = "Seleziona la tua lingua preferita",
            adBlockActive = "AdBlock Attivo",
            adBlockSub = "Annunci pubblicitari YouTube bloccati.",
            advertsAllowed = "Annunci Consentiti",
            advertsAllowedSub = "I normali annunci YouTube vengono riprodotti.",
            closeBtn = "Chiudi",
            cancelBtn = "Annulla",
            deleteBtn = "Elimina",
            tryOurOtherProducts = "Prova i Nostri Altri Prodotti ✨",
            otherProductsSub = "Scopri altri strumenti creati dal nostro team:",
            visitWebsite = "Visita il Sito 🌐",
            getOnStore = "Vedi sullo Store 🛒"
        ),
        AppLanguage.RU to AppStrings(
            navHome = "Главная",
            navSubscriptions = "Подписки",
            navLibrary = "Библиотека",
            navShorts = "Shorts",
            navSearch = "Поиск",
            tabSubjects = "Темы",
            tabDownloads = "Загрузки",
            tabFavorites = "Избранное",
            tabWatchLater = "Смотреть позже",
            tabHistory = "История",
            libraryTitle = "Библиотека и Загрузки",
            customPlaylists = "Пользовательские плейлисты",
            addCategory = "Добавить категорию",
            noFavoritesText = "Нет избранных видео. Нажмите на звездочку, чтобы сохранить!",
            noWatchLaterText = "Список 'Смотреть позже' пуст.",
            noHistoryText = "История просмотров пока пуста.",
            noDownloadsText = "Нет загруженных видео. Скачивайте видео для просмотра офлайн.",
            catAll = "Все",
            catLast24h = "⏰ За 24 часа",
            catTechCode = "Технологии и Код",
            catMusic = "Музыка",
            catTutorials = "Обучение",
            catGaming = "Игры",
            catFocusAmbient = "Концентрация и Фон",
            btnAiSummary = "ИИ-Сводка",
            btnDownload = "Скачать",
            btnDownloaded = "Скачано",
            btnShare = "Поделиться",
            btnLike = "Нравится",
            btnLiked = "Понравилось",
            btnDislike = "Не нравится",
            btnDisliked = "Скрыто",
            sponsorSkipped = "Спонсор пропущен",
            settingsTitle = "Настройки и Алгоритм",
            appLanguageTitle = "Язык приложения",
            appLanguageSub = "Выберите язык для мгновенного обновления интерфейса",
            adBlockActive = "AdBlock активен",
            adBlockSub = "Коммерческая реклама YouTube блокируется.",
            advertsAllowed = "Реклама разрешена",
            advertsAllowedSub = "Стандартная реклама воспроизводится.",
            closeBtn = "Закрыть",
            cancelBtn = "Отмена",
            deleteBtn = "Удалить",
            tryOurOtherProducts = "Попробуйте другие наши продукты ✨",
            otherProductsSub = "Узнайте о других мощных инструментах от нашей команды:",
            visitWebsite = "Перейти на сайт 🌐",
            getOnStore = "Открыть в магазине 🛒"
        ),
        AppLanguage.JA to AppStrings(
            navHome = "ホーム",
            navSubscriptions = "登録チャンネル",
            navLibrary = "ライブラリ",
            navShorts = "Shorts",
            navSearch = "検索",
            tabSubjects = "カテゴリ",
            tabDownloads = "ダウンロード",
            tabFavorites = "お気に入り",
            tabWatchLater = "後で見る",
            tabHistory = "再生履歴",
            libraryTitle = "ライブラリ＆保存済み",
            customPlaylists = "カスタムプレイリスト",
            addCategory = "カテゴリを追加",
            noFavoritesText = "お気に入りの動画はまだありません。星アイコンをタップして保存しましょう！",
            noWatchLaterText = "「後で見る」リストは空です。",
            noHistoryText = "再生履歴はまだありません。視聴した動画はここに表示されます。",
            noDownloadsText = "保存された動画はありません。オフライン再生用にダウンロードしてください。",
            catAll = "すべて",
            catLast24h = "⏰ 過去24時間",
            catTechCode = "テクノロジー＆プログラミング",
            catMusic = "音楽",
            catTutorials = "チュートリアル",
            catGaming = "ゲーム",
            catFocusAmbient = "集中＆環境音",
            btnAiSummary = "AI要約",
            btnDownload = "保存",
            btnDownloaded = "保存済み",
            btnShare = "共有",
            btnLike = "高評価",
            btnLiked = "評価済み",
            btnDislike = "低評価",
            btnDisliked = "非表示",
            sponsorSkipped = "スポンサーをスキップ",
            settingsTitle = "設定＆おすすめ調整",
            appLanguageTitle = "アプリ言語",
            appLanguageSub = "表示言語を選択すると即座にUIが切り替わります",
            adBlockActive = "広告ブロック有効",
            adBlockSub = "YouTubeの商業広告を自動的にブロックします。",
            advertsAllowed = "広告を許可",
            advertsAllowedSub = "通常の広告が再生されます。",
            closeBtn = "閉じる",
            cancelBtn = "キャンセル",
            deleteBtn = "削除",
            tryOurOtherProducts = "他の製品も試してみる ✨",
            otherProductsSub = "私たちのチームが開発した他のツールをご覧ください:",
            visitWebsite = "公式サイト 🌐",
            getOnStore = "ストアで見る 🛒"
        ),
        AppLanguage.KO to AppStrings(
            navHome = "홈",
            navSubscriptions = "구독",
            navLibrary = "보관함",
            navShorts = "Shorts",
            navSearch = "검색",
            tabSubjects = "주제별",
            tabDownloads = "오프라인 저장",
            tabFavorites = "좋아요한 동영상",
            tabWatchLater = "나중에 볼 동영상",
            tabHistory = "시청 기록",
            libraryTitle = "보관함 및 다운로드",
            customPlaylists = "맞춤 재생목록",
            addCategory = "카테고리 추가",
            noFavoritesText = "아직 좋아요한 동영상이 없습니다. 별표 아이콘을 눌러 저장해보세요!",
            noWatchLaterText = "나중에 볼 동영상 목록이 비어 있습니다.",
            noHistoryText = "시청 기록이 없습니다.",
            noDownloadsText = "오프라인 저장된 동영상이 없습니다.",
            catAll = "전체",
            catLast24h = "⏰ 최근 24시간",
            catTechCode = "기술 및 코딩",
            catMusic = "음악",
            catTutorials = "튜토리얼",
            catGaming = "게임",
            catFocusAmbient = "집중 및 배경음악",
            btnAiSummary = "AI 요약",
            btnDownload = "오프라인 저장",
            btnDownloaded = "저장 완료",
            btnShare = "공유",
            btnLike = "좋아요",
            btnLiked = "좋아요 표시함",
            btnDislike = "싫어요",
            btnDisliked = "숨김 처리됨",
            sponsorSkipped = "스폰서 구간 건너뜀",
            settingsTitle = "설정 및 알고리즘",
            appLanguageTitle = "앱 언어",
            appLanguageSub = "원하는 언어를 선택하면 즉시 UI가 반영됩니다",
            adBlockActive = "광고 차단 활성",
            adBlockSub = "유튜브 상업 광고를 건너뜁니다.",
            advertsAllowed = "광고 허용",
            advertsAllowedSub = "일반 광고가 재생됩니다.",
            closeBtn = "닫기",
            cancelBtn = "취소",
            deleteBtn = "삭제",
            tryOurOtherProducts = "다른 추천 앱 사용해보기 ✨",
            otherProductsSub = "저희 팀에서 제작한 다른 유용한 앱들을 만나보세요:",
            visitWebsite = "웹사이트 방문 🌐",
            getOnStore = "스토어에서 보기 🛒"
        ),
        AppLanguage.ZH to AppStrings(
            navHome = "首页",
            navSubscriptions = "订阅",
            navLibrary = "媒体库",
            navShorts = "Shorts",
            navSearch = "搜索",
            tabSubjects = "主题分类",
            tabDownloads = "离线缓存",
            tabFavorites = "我的收藏",
            tabWatchLater = "稍后观看",
            tabHistory = "观看历史",
            libraryTitle = "媒体库与下载",
            customPlaylists = "自定义播单",
            addCategory = "新建分类",
            noFavoritesText = "暂无收藏视频。点击视频上的星标即可添加到这里！",
            noWatchLaterText = "稍后观看列表为空。",
            noHistoryText = "暂无观看历史记录。",
            noDownloadsText = "暂无下载视频。点击下载按钮可离线随时观看。",
            catAll = "全部",
            catLast24h = "⏰ 24小时内",
            catTechCode = "科技与编程",
            catMusic = "音乐旋律",
            catTutorials = "实用教程",
            catGaming = "游戏天地",
            catFocusAmbient = "专注与白噪音",
            btnAiSummary = "AI 摘要",
            btnDownload = "下载视频",
            btnDownloaded = "已下载",
            btnShare = "分享",
            btnLike = "赞",
            btnLiked = "已点赞",
            btnDislike = "踩",
            btnDisliked = "已隐藏",
            sponsorSkipped = "已跳过赞助片段",
            settingsTitle = "应用设置与算法",
            appLanguageTitle = "显示语言",
            appLanguageSub = "选择您的首选语言，界面将即刻切换",
            adBlockActive = "广告拦截已启用",
            adBlockSub = "自动拦截 YouTube 商业广告。",
            advertsAllowed = "允许广告",
            advertsAllowedSub = "播放常规广告内容。",
            closeBtn = "关闭",
            cancelBtn = "取消",
            deleteBtn = "删除",
            tryOurOtherProducts = "探索我们的其他产品 ✨",
            otherProductsSub = "发现由我们团队开发的更多出色工具：",
            visitWebsite = "访问官网 🌐",
            getOnStore = "在应用商店获取 🛒"
        ),
        AppLanguage.HI to AppStrings(
            navHome = "होम",
            navSubscriptions = "सदस्यताएँ",
            navLibrary = "लाइब्रेरी",
            navShorts = "Shorts",
            navSearch = "खोजें",
            tabSubjects = "विषय",
            tabDownloads = "डाउनलोड",
            tabFavorites = "पसंदीदा",
            tabWatchLater = "बाद में देखें",
            tabHistory = "इतिहास",
            libraryTitle = "लाइब्रेरी और डाउनलोड",
            customPlaylists = "कस्टम प्लेलिस्ट",
            addCategory = "श्रेणी जोड़ें",
            noFavoritesText = "अभी तक कोई पसंदीदा वीडियो सहेजा नहीं गया है!",
            noWatchLaterText = "आपकी बाद में देखने की सूची खाली है।",
            noHistoryText = "कोई इतिहास दर्ज नहीं है।",
            noDownloadsText = "कोई डाउनलोड नहीं है।",
            catAll = "सभी",
            catLast24h = "⏰ पिछले 24 घंटे",
            catTechCode = "तकनीक और कोड",
            catMusic = "संगीत",
            catTutorials = "ट्यूटोरियल",
            catGaming = "गेमिंग",
            catFocusAmbient = "ध्यान और वातावरण",
            btnAiSummary = "एआई सारांश",
            btnDownload = "डाउनलोड",
            btnDownloaded = "डाउनलोड किया गया",
            btnShare = "शेयर करें",
            btnLike = "पसंद",
            btnLiked = "पसंद किया",
            btnDislike = "नापसंद",
            btnDisliked = "छिपाया गया",
            sponsorSkipped = "प्रायोजक छोड़ा गया",
            settingsTitle = "सेटिंग्स और एल्गोरिदम",
            appLanguageTitle = "ऐप की भाषा",
            appLanguageSub = "तत्काल भाषा बदलने के लिए अपनी पसंदीदा भाषा चुनें",
            adBlockActive = "एडब्लॉक सक्रिय",
            adBlockSub = "व्यावसायिक विज्ञापन अवरुद्ध हैं।",
            advertsAllowed = "विज्ञापनों की अनुमति है",
            advertsAllowedSub = "सामान्य विज्ञापन दिखाए जाएंगे।",
            closeBtn = "बंद करें",
            cancelBtn = "रद्द करें",
            deleteBtn = "हटाएं",
            tryOurOtherProducts = "हमारे अन्य उत्पाद आज़माएँ ✨",
            otherProductsSub = "हमारी टीम द्वारा बनाए गए अन्य शक्तिशाली उपकरण देखें:",
            visitWebsite = "वेबसाइट देखें 🌐",
            getOnStore = "स्टोर पर प्राप्त करें 🛒"
        ),
        AppLanguage.AR to AppStrings(
            navHome = "الرئيسية",
            navSubscriptions = "الاشتراكات",
            navLibrary = "المكتبة",
            navShorts = "Shorts",
            navSearch = "بحث",
            tabSubjects = "المواضيع",
            tabDownloads = "التنزيلات",
            tabFavorites = "المفضلة",
            tabWatchLater = "المشاهدة لاحقاً",
            tabHistory = "السجل",
            libraryTitle = "المكتبة والتنزيلات",
            customPlaylists = "قوائم التشغيل المخصصة",
            addCategory = "إضافة قسم",
            noFavoritesText = "لم يتم حفظ أي فيديوهات مفضلة بعد!",
            noWatchLaterText = "قائمة المشاهدة لاحقاً فارغة.",
            noHistoryText = "لا يوجد سجل مشاهدة بعد.",
            noDownloadsText = "لا توجد تنزيلات.",
            catAll = "الكل",
            catLast24h = "⏰ آخر 24 ساعة",
            catTechCode = "التقنية والبرمجة",
            catMusic = "الموسيقى",
            catTutorials = "الدروس التعليمية",
            catGaming = "الألعاب",
            catFocusAmbient = "التركيز والاسترخاء",
            btnAiSummary = "ملخص الذكاء الاصطناعي",
            btnDownload = "تنزيل",
            btnDownloaded = "تم التنزيل",
            btnShare = "مشاركة",
            btnLike = "إعجاب",
            btnLiked = "تم الإعجاب",
            btnDislike = "لم يعجبني",
            btnDisliked = "تم الإخفاء",
            sponsorSkipped = "تم تخطي الإعلان المدمج",
            settingsTitle = "الإعدادات والخوارزمية",
            appLanguageTitle = "لغة التطبيق",
            appLanguageSub = "اختر لغتك المفضلة للتحديث الفوري للواجهة",
            adBlockActive = "مانع الإعلانات نشط",
            adBlockSub = "تم حظر إعلانات يوتيوب التجارية.",
            advertsAllowed = "السماح بالإعلانات",
            advertsAllowedSub = "يتم تشغيل الإعلانات العادية.",
            closeBtn = "إغلاق",
            cancelBtn = "إلغاء",
            deleteBtn = "حذف",
            tryOurOtherProducts = "جرّب منتجاتنا الأخرى ✨",
            otherProductsSub = "اكتشف المزيد من الأدوات القوية التي طورها فريقنا:",
            visitWebsite = "زيارة الموقع 🌐",
            getOnStore = "الحصول عليه من المتجر 🛒"
        )
    )

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLangName = prefs.getString(KEY_LANGUAGE, AppLanguage.EN.name) ?: AppLanguage.EN.name
        val lang = try {
            AppLanguage.valueOf(savedLangName)
        } catch (e: Exception) {
            AppLanguage.EN
        }
        _currentLanguage.value = lang
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        _currentLanguage.value = language
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun getStrings(language: AppLanguage): AppStrings {
        return translations[language] ?: translations[AppLanguage.EN]!!
    }

    /**
     * Translates dynamic relative publication time (e.g. "2 years ago", "1 month ago", "3 days ago", "5 hours ago", "just now")
     * into the selected application language.
     */
    fun localizeRelativeTime(raw: String?, lang: AppLanguage): String {
        if (raw.isNullOrBlank()) return ""
        if (lang == AppLanguage.EN) return raw

        val lower = raw.trim().lowercase()
        val isStreamed = lower.contains("streamed") || lower.contains("en vivo") || lower.contains("direct")

        if (lower.contains("just now") || lower.contains("moments ago") || lower.contains("now")) {
            return when (lang) {
                AppLanguage.ES -> "hace un momento"
                AppLanguage.FR -> "à l'instant"
                AppLanguage.DE -> "gerade eben"
                AppLanguage.PT -> "agora mesmo"
                AppLanguage.IT -> "proprio ora"
                AppLanguage.RU -> "только что"
                AppLanguage.JA -> "たった今"
                AppLanguage.KO -> "방금 전"
                AppLanguage.ZH -> "刚刚"
                AppLanguage.HI -> "अभी-अभी"
                AppLanguage.AR -> "الآن"
                else -> raw
            }
        }

        if (lower.contains("yesterday")) {
            return when (lang) {
                AppLanguage.ES -> "ayer"
                AppLanguage.FR -> "hier"
                AppLanguage.DE -> "gestern"
                AppLanguage.PT -> "ontem"
                AppLanguage.IT -> "ieri"
                AppLanguage.RU -> "вчера"
                AppLanguage.JA -> "昨日"
                AppLanguage.KO -> "어제"
                AppLanguage.ZH -> "昨天"
                AppLanguage.HI -> "कल"
                AppLanguage.AR -> "أمس"
                else -> raw
            }
        }

        if (lower.contains("today")) {
            return when (lang) {
                AppLanguage.ES -> "hoy"
                AppLanguage.FR -> "aujourd'hui"
                AppLanguage.DE -> "heute"
                AppLanguage.PT -> "hoje"
                AppLanguage.IT -> "oggi"
                AppLanguage.RU -> "сегодня"
                AppLanguage.JA -> "今日"
                AppLanguage.KO -> "오늘"
                AppLanguage.ZH -> "今天"
                AppLanguage.HI -> "आज"
                AppLanguage.AR -> "اليوم"
                else -> raw
            }
        }

        val pattern = Pattern.compile("""(\d+)\s*(second|sec|minute|min|hour|hr|day|week|month|year)s?\s*ago""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(lower)

        if (matcher.find()) {
            val num = matcher.group(1)?.toLongOrNull() ?: 1L
            val unit = matcher.group(2)?.lowercase() ?: "day"
            val result = formatRelativeUnit(num, unit, lang)
            return if (isStreamed) {
                val prefix = when (lang) {
                    AppLanguage.ES -> "Emitido "
                    AppLanguage.FR -> "Diffusé "
                    AppLanguage.DE -> "Gestreamt "
                    AppLanguage.PT -> "Transmitido "
                    AppLanguage.IT -> "Trasmesso "
                    AppLanguage.RU -> "Транслировалось "
                    AppLanguage.JA -> "配信済み "
                    AppLanguage.KO -> "스트리밍 완료: "
                    AppLanguage.ZH -> "已直播 "
                    AppLanguage.HI -> "लाइव स्ट्रीम "
                    AppLanguage.AR -> "تم البث "
                    else -> "Streamed "
                }
                prefix + result
            } else {
                result
            }
        }

        return raw
    }

    private fun formatRelativeUnit(num: Long, unit: String, lang: AppLanguage): String {
        val isSec = unit.startsWith("sec")
        val isMin = unit.startsWith("min")
        val isHour = unit.startsWith("hour") || unit.startsWith("hr")
        val isDay = unit.startsWith("day")
        val isWeek = unit.startsWith("week")
        val isMonth = unit.startsWith("month")
        val isYear = unit.startsWith("year")

        return when (lang) {
            AppLanguage.ES -> when {
                isSec -> "hace $num segundo${if (num != 1L) "s" else ""}"
                isMin -> "hace $num minuto${if (num != 1L) "s" else ""}"
                isHour -> "hace $num hora${if (num != 1L) "s" else ""}"
                isDay -> if (num == 1L) "hace 1 día" else "hace $num días"
                isWeek -> "hace $num semana${if (num != 1L) "s" else ""}"
                isMonth -> if (num == 1L) "hace 1 mes" else "hace $num meses"
                isYear -> if (num == 1L) "hace 1 año" else "hace $num años"
                else -> "hace $num $unit"
            }
            AppLanguage.FR -> when {
                isSec -> "il y a $num seconde${if (num > 1L) "s" else ""}"
                isMin -> "il y a $num minute${if (num > 1L) "s" else ""}"
                isHour -> "il y a $num heure${if (num > 1L) "s" else ""}"
                isDay -> "il y a $num jour${if (num > 1L) "s" else ""}"
                isWeek -> "il y a $num semaine${if (num > 1L) "s" else ""}"
                isMonth -> "il y a $num mois"
                isYear -> "il y a $num an${if (num > 1L) "s" else ""}"
                else -> "il y a $num $unit"
            }
            AppLanguage.DE -> when {
                isSec -> "vor $num Sekunde${if (num != 1L) "n" else ""}"
                isMin -> "vor $num Minute${if (num != 1L) "n" else ""}"
                isHour -> "vor $num Stunde${if (num != 1L) "n" else ""}"
                isDay -> if (num == 1L) "vor 1 Tag" else "vor $num Tagen"
                isWeek -> "vor $num Woche${if (num != 1L) "n" else ""}"
                isMonth -> if (num == 1L) "vor 1 Monat" else "vor $num Monaten"
                isYear -> if (num == 1L) "vor 1 Jahr" else "vor $num Jahren"
                else -> "vor $num $unit"
            }
            AppLanguage.PT -> when {
                isSec -> "há $num segundo${if (num != 1L) "s" else ""}"
                isMin -> "há $num minuto${if (num != 1L) "s" else ""}"
                isHour -> "há $num hora${if (num != 1L) "s" else ""}"
                isDay -> if (num == 1L) "há 1 dia" else "há $num dias"
                isWeek -> "há $num semana${if (num != 1L) "s" else ""}"
                isMonth -> if (num == 1L) "há 1 mês" else "há $num meses"
                isYear -> if (num == 1L) "há 1 ano" else "há $num anos"
                else -> "há $num $unit"
            }
            AppLanguage.IT -> when {
                isSec -> "$num second${if (num == 1L) "o" else "i"} fa"
                isMin -> "$num minut${if (num == 1L) "o" else "i"} fa"
                isHour -> "$num or${if (num == 1L) "a" else "e"} fa"
                isDay -> "$num giorn${if (num == 1L) "o" else "i"} fa"
                isWeek -> "$num settiman${if (num == 1L) "a" else "e"} fa"
                isMonth -> "$num mes${if (num == 1L) "e" else "i"} fa"
                isYear -> "$num ann${if (num == 1L) "o" else "i"} fa"
                else -> "$num $unit fa"
            }
            AppLanguage.RU -> when {
                isSec -> "$num сек. назад"
                isMin -> "$num мин. назад"
                isHour -> "$num ч. назад"
                isDay -> if (num == 1L) "1 день назад" else "$num дн. назад"
                isWeek -> "$num нед. назад"
                isMonth -> "$num мес. назад"
                isYear -> if (num == 1L) "1 год назад" else "$num г. назад"
                else -> "$num назад"
            }
            AppLanguage.JA -> when {
                isSec -> "${num}秒前"
                isMin -> "${num}分前"
                isHour -> "${num}時間前"
                isDay -> "${num}日前"
                isWeek -> "${num}週間前"
                isMonth -> "${num}か月前"
                isYear -> "${num}年前"
                else -> "${num}前"
            }
            AppLanguage.KO -> when {
                isSec -> "${num}초 전"
                isMin -> "${num}분 전"
                isHour -> "${num}시간 전"
                isDay -> "${num}일 전"
                isWeek -> "${num}주 전"
                isMonth -> "${num}개월 전"
                isYear -> "${num}년 전"
                else -> "${num} 전"
            }
            AppLanguage.ZH -> when {
                isSec -> "$num 秒前"
                isMin -> "$num 分钟前"
                isHour -> "$num 小时前"
                isDay -> "$num 天前"
                isWeek -> "$num 周前"
                isMonth -> "$num 个月前"
                isYear -> "$num 年前"
                else -> "$num 前"
            }
            AppLanguage.HI -> when {
                isSec -> "$num सेकंड पहले"
                isMin -> "$num मिनट पहले"
                isHour -> "$num घंटे पहले"
                isDay -> "$num दिन पहले"
                isWeek -> "$num सप्ताह पहले"
                isMonth -> "$num महीने पहले"
                isYear -> "$num साल पहले"
                else -> "$num पहले"
            }
            AppLanguage.AR -> when {
                isSec -> if (num == 1L) "منذ ثانية" else "منذ $num ثانية"
                isMin -> if (num == 1L) "منذ دقيقة" else "منذ $num دقيقة"
                isHour -> if (num == 1L) "منذ ساعة" else "منذ $num ساعة"
                isDay -> if (num == 1L) "منذ يوم" else if (num == 2L) "منذ يومين" else "منذ $num أيام"
                isWeek -> if (num == 1L) "منذ أسبوع" else "منذ $num أسابيع"
                isMonth -> if (num == 1L) "منذ شهر" else "منذ $num أشهر"
                isYear -> if (num == 1L) "منذ سنة" else "منذ $num سنوات"
                else -> "منذ $num $unit"
            }
            else -> "$num $unit ago"
        }
    }

    /**
     * Translates view count strings (e.g. "1.2M views", "500K views", "1,234 views", "No views")
     * into the selected application language.
     */
    fun localizeViewCount(raw: String?, lang: AppLanguage): String {
        if (raw.isNullOrBlank()) return ""
        if (lang == AppLanguage.EN) return raw

        val lower = raw.trim().lowercase()

        if (lower.contains("no views")) {
            return when (lang) {
                AppLanguage.ES -> "Sin vistas"
                AppLanguage.FR -> "Aucune vue"
                AppLanguage.DE -> "Keine Aufrufe"
                AppLanguage.PT -> "Nenhuma visualização"
                AppLanguage.IT -> "Nessuna visualizzazione"
                AppLanguage.RU -> "Нет просмотров"
                AppLanguage.JA -> "視聴回数なし"
                AppLanguage.KO -> "조회수 없음"
                AppLanguage.ZH -> "暂无观看"
                AppLanguage.HI -> "कोई दृश्य नहीं"
                AppLanguage.AR -> "بلا مشاهدات"
                else -> raw
            }
        }

        val pattern = Pattern.compile("""^([\d\.,]+(?:\s*[kmbt])?)\s*(?:views|view)?""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(lower)
        if (matcher.find()) {
            val countPart = matcher.group(1)?.trim()?.uppercase() ?: ""
            return when (lang) {
                AppLanguage.ES -> "$countPart visualizaciones"
                AppLanguage.FR -> "$countPart vues"
                AppLanguage.DE -> "$countPart Aufrufe"
                AppLanguage.PT -> "$countPart visualizações"
                AppLanguage.IT -> "$countPart visualizzazioni"
                AppLanguage.RU -> "$countPart просмотров"
                AppLanguage.JA -> "${countPart}回視聴"
                AppLanguage.KO -> "${countPart}회 조회"
                AppLanguage.ZH -> "$countPart 次观看"
                AppLanguage.HI -> "$countPart दृश्य"
                AppLanguage.AR -> "$countPart مشاهدة"
                else -> "$countPart views"
            }
        }

        return raw
    }
}

val LocalAppStrings = compositionLocalOf { AppStrings() }
val LocalAppLanguage = compositionLocalOf { AppLanguage.EN }
