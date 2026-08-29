using System;
using System.Collections.Generic;

namespace VixzDesktop.Services
{
    public class DesktopStrings
    {
        public string HomeFeed { get; set; } = "Home Feed";
        public string Subscriptions { get; set; } = "Subscriptions";
        public string Trending { get; set; } = "Trending";
        public string Favorites { get; set; } = "Favorites";
        public string WatchLater { get; set; } = "Watch Later";
        public string History { get; set; } = "History";
        public string RecommendedFeed { get; set; } = "Recommended Feed";
        public string SearchPlaceholder { get; set; } = "Search videos, channels, topics...";
        public string SignIn { get; set; } = "Sign In";
        public string DateLabel { get; set; } = "Date:";
        public string LengthLabel { get; set; } = "Length:";
        public string SortLabel { get; set; } = "Sort:";
        public string ApplyFilters { get; set; } = "Apply Filters";
        public string Reset { get; set; } = "Reset";
        public string SaveDestination { get; set; } = "Save Destination";
        public string ChangeFolder { get; set; } = "Change";
        public string OpenFolder { get; set; } = "Open ↗";
        public string TryOtherProducts { get; set; } = "Try Our Other Products ✨";
        public string LanguageTitle { get; set; } = "App Language";
    }

    public static class DesktopLocalizationService
    {
        public static readonly Dictionary<string, (string DisplayName, string Flag, string NativeName)> AvailableLanguages = 
            new Dictionary<string, (string, string, string)>(StringComparer.OrdinalIgnoreCase)
        {
            { "EN", ("English", "🇺🇸", "English") },
            { "ES", ("Español", "🇪🇸", "Español") },
            { "FR", ("Français", "🇫🇷", "Français") },
            { "DE", ("Deutsch", "🇩🇪", "Deutsch") },
            { "PT", ("Português", "🇧🇷", "Português") },
            { "IT", ("Italiano", "🇮🇹", "Italiano") },
            { "RU", ("Русский", "🇷🇺", "Русский") },
            { "JA", ("日本語", "🇯🇵", "日本語") },
            { "KO", ("한국어", "🇰🇷", "한국어") },
            { "ZH", ("简体中文", "🇨🇳", "简体中文") },
            { "HI", ("हिन्दी", "🇮🇳", "हिन्दी") },
            { "AR", ("العربية", "🇸🇦", "العربية") }
        };

        private static readonly Dictionary<string, DesktopStrings> Translations = 
            new Dictionary<string, DesktopStrings>(StringComparer.OrdinalIgnoreCase)
        {
            {
                "EN", new DesktopStrings()
            },
            {
                "ES", new DesktopStrings
                {
                    HomeFeed = "Inicio",
                    Subscriptions = "Suscripciones",
                    Trending = "Tendencias",
                    Favorites = "Favoritos",
                    WatchLater = "Ver Más Tarde",
                    History = "Historial",
                    RecommendedFeed = "Feed Recomendado",
                    SearchPlaceholder = "Buscar videos, canales, temas...",
                    SignIn = "Iniciar Sesión",
                    DateLabel = "Fecha:",
                    LengthLabel = "Duración:",
                    SortLabel = "Orden:",
                    ApplyFilters = "Aplicar Filtros",
                    Reset = "Restablecer",
                    SaveDestination = "Destino de Guardado",
                    ChangeFolder = "Cambiar",
                    OpenFolder = "Abrir ↗",
                    TryOtherProducts = "Prueba Nuestros Otros Productos ✨",
                    LanguageTitle = "Idioma"
                }
            },
            {
                "FR", new DesktopStrings
                {
                    HomeFeed = "Accueil",
                    Subscriptions = "Abonnements",
                    Trending = "Tendances",
                    Favorites = "Favoris",
                    WatchLater = "À regarder plus tard",
                    History = "Historique",
                    RecommendedFeed = "Flux Recommandé",
                    SearchPlaceholder = "Rechercher des vidéos, chaînes, sujets...",
                    SignIn = "Connexion",
                    DateLabel = "Date:",
                    LengthLabel = "Durée:",
                    SortLabel = "Trier:",
                    ApplyFilters = "Appliquer Filtres",
                    Reset = "Réinitialiser",
                    SaveDestination = "Dossier d'enregistrement",
                    ChangeFolder = "Modifier",
                    OpenFolder = "Ouvrir ↗",
                    TryOtherProducts = "Essayez Nos Autres Produits ✨",
                    LanguageTitle = "Langue"
                }
            },
            {
                "DE", new DesktopStrings
                {
                    HomeFeed = "Startseite",
                    Subscriptions = "Abos",
                    Trending = "Trends",
                    Favorites = "Favoriten",
                    WatchLater = "Später ansehen",
                    History = "Verlauf",
                    RecommendedFeed = "Empfohlener Feed",
                    SearchPlaceholder = "Videos, Kanäle, Themen suchen...",
                    SignIn = "Anmelden",
                    DateLabel = "Datum:",
                    LengthLabel = "Dauer:",
                    SortLabel = "Sortieren:",
                    ApplyFilters = "Filter anwenden",
                    Reset = "Zurücksetzen",
                    SaveDestination = "Speicherort",
                    ChangeFolder = "Ändern",
                    OpenFolder = "Öffnen ↗",
                    TryOtherProducts = "Entdecke Unsere Weiteren Produkte ✨",
                    LanguageTitle = "Sprache"
                }
            },
            {
                "PT", new DesktopStrings
                {
                    HomeFeed = "Início",
                    Subscriptions = "Inscrições",
                    Trending = "Em Alta",
                    Favorites = "Favoritos",
                    WatchLater = "Assistir Mais Tarde",
                    History = "Histórico",
                    RecommendedFeed = "Feed Recomendado",
                    SearchPlaceholder = "Pesquisar vídeos, canais, tópicos...",
                    SignIn = "Entrar",
                    DateLabel = "Data:",
                    LengthLabel = "Duração:",
                    SortLabel = "Ordenar:",
                    ApplyFilters = "Aplicar Filtros",
                    Reset = "Redefinir",
                    SaveDestination = "Destino de Salvamento",
                    ChangeFolder = "Alterar",
                    OpenFolder = "Abrir ↗",
                    TryOtherProducts = "Experimente Nossos Outros Produtos ✨",
                    LanguageTitle = "Idioma"
                }
            },
            {
                "IT", new DesktopStrings
                {
                    HomeFeed = "Home",
                    Subscriptions = "Iscrizioni",
                    Trending = "Tendenze",
                    Favorites = "Preferiti",
                    WatchLater = "Guarda Più Tardi",
                    History = "Cronologia",
                    RecommendedFeed = "Feed Consigliato",
                    SearchPlaceholder = "Cerca video, canali, argomenti...",
                    SignIn = "Accedi",
                    DateLabel = "Data:",
                    LengthLabel = "Durata:",
                    SortLabel = "Ordina:",
                    ApplyFilters = "Applica Filtri",
                    Reset = "Reimposta",
                    SaveDestination = "Cartella di Salvataggio",
                    ChangeFolder = "Modifica",
                    OpenFolder = "Apri ↗",
                    TryOtherProducts = "Prova i Nostri Altri Prodotti ✨",
                    LanguageTitle = "Lingua"
                }
            },
            {
                "RU", new DesktopStrings
                {
                    HomeFeed = "Главная",
                    Subscriptions = "Подписки",
                    Trending = "В тренде",
                    Favorites = "Избранное",
                    WatchLater = "Смотреть позже",
                    History = "История",
                    RecommendedFeed = "Рекомендации",
                    SearchPlaceholder = "Поиск видео, каналов, тем...",
                    SignIn = "Войти",
                    DateLabel = "Дата:",
                    LengthLabel = "Длина:",
                    SortLabel = "Сортировка:",
                    ApplyFilters = "Применить",
                    Reset = "Сброс",
                    SaveDestination = "Папка сохранения",
                    ChangeFolder = "Изменить",
                    OpenFolder = "Открыть ↗",
                    TryOtherProducts = "Попробуйте другие наши продукты ✨",
                    LanguageTitle = "Язык"
                }
            },
            {
                "JA", new DesktopStrings
                {
                    HomeFeed = "ホーム",
                    Subscriptions = "登録チャンネル",
                    Trending = "急上昇",
                    Favorites = "お気に入り",
                    WatchLater = "後で見る",
                    History = "再生履歴",
                    RecommendedFeed = "おすすめフィード",
                    SearchPlaceholder = "動画、チャンネル、トピックを検索...",
                    SignIn = "ログイン",
                    DateLabel = "日付:",
                    LengthLabel = "長さ:",
                    SortLabel = "並び替え:",
                    ApplyFilters = "フィルター適用",
                    Reset = "リセット",
                    SaveDestination = "保存先フォルダ",
                    ChangeFolder = "変更",
                    OpenFolder = "開く ↗",
                    TryOtherProducts = "他の製品も試してみる ✨",
                    LanguageTitle = "言語"
                }
            },
            {
                "KO", new DesktopStrings
                {
                    HomeFeed = "홈 피드",
                    Subscriptions = "구독 채널",
                    Trending = "인기 급상승",
                    Favorites = "좋아요한 동영상",
                    WatchLater = "나중에 볼 동영상",
                    History = "시청 기록",
                    RecommendedFeed = "맞춤 추천 피드",
                    SearchPlaceholder = "동영상, 채널, 주제 검색...",
                    SignIn = "로그인",
                    DateLabel = "업로드 날짜:",
                    LengthLabel = "재생 시간:",
                    SortLabel = "정렬 기준:",
                    ApplyFilters = "필터 적용",
                    Reset = "초기화",
                    SaveDestination = "저장 위치",
                    ChangeFolder = "변경",
                    OpenFolder = "열기 ↗",
                    TryOtherProducts = "다른 추천 앱 사용해보기 ✨",
                    LanguageTitle = "언어"
                }
            },
            {
                "ZH", new DesktopStrings
                {
                    HomeFeed = "首页推荐",
                    Subscriptions = "订阅频道",
                    Trending = "时下流行",
                    Favorites = "收藏夹",
                    WatchLater = "稍后观看",
                    History = "播放历史",
                    RecommendedFeed = "为你推荐",
                    SearchPlaceholder = "搜索视频、频道、主题...",
                    SignIn = "登录账号",
                    DateLabel = "发布时间:",
                    LengthLabel = "视频时长:",
                    SortLabel = "排序方式:",
                    ApplyFilters = "应用筛选",
                    Reset = "重置",
                    SaveDestination = "保存路径",
                    ChangeFolder = "更改",
                    OpenFolder = "打开 ↗",
                    TryOtherProducts = "体验我们的其他产品 ✨",
                    LanguageTitle = "语言"
                }
            },
            {
                "HI", new DesktopStrings
                {
                    HomeFeed = "होम फ़ीड",
                    Subscriptions = "सब्सक्रिप्शन",
                    Trending = "ट्रेंडिंग",
                    Favorites = "पसंदीदा",
                    WatchLater = "बाद में देखें",
                    History = "इतिहास",
                    RecommendedFeed = "सुझाई गई फ़ीड",
                    SearchPlaceholder = "वीडियो, चैनल, विषय खोजें...",
                    SignIn = "साइन इन करें",
                    DateLabel = "दिनांक:",
                    LengthLabel = "अवधि:",
                    SortLabel = "क्रमबद्ध करें:",
                    ApplyFilters = "फ़िल्टर लागू करें",
                    Reset = "रीसेट",
                    SaveDestination = "सेव स्थान",
                    ChangeFolder = "बदलें",
                    OpenFolder = "खोलें ↗",
                    TryOtherProducts = "हमारे अन्य उत्पाद आज़माएं ✨",
                    LanguageTitle = "भाषा"
                }
            },
            {
                "AR", new DesktopStrings
                {
                    HomeFeed = "الصفحة الرئيسية",
                    Subscriptions = "الاشتراكات",
                    Trending = "شائع الآن",
                    Favorites = "المفضلة",
                    WatchLater = "المشاهدة لاحقاً",
                    History = "سجل المشاهدة",
                    RecommendedFeed = "الفيديوهات المقترحة",
                    SearchPlaceholder = "البحث عن مقاطع فيديو، قنوات، مواضيع...",
                    SignIn = "تسجيل الدخول",
                    DateLabel = "التاريخ:",
                    LengthLabel = "المدة:",
                    SortLabel = "الترتيب:",
                    ApplyFilters = "تطبيق الفلاتر",
                    Reset = "إعادة تعيين",
                    SaveDestination = "مجلد الحفظ",
                    ChangeFolder = "تغيير",
                    OpenFolder = "فتح ↗",
                    TryOtherProducts = "جرب منتجاتنا الأخرى ✨",
                    LanguageTitle = "اللغة"
                }
            }
        };

        public static DesktopStrings GetStrings(string langCode)
        {
            if (Translations.TryGetValue(langCode, out var strings))
                return strings;
            return Translations["EN"];
        }
    }
}
