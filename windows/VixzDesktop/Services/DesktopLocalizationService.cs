using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace VixzDesktop.Services
{
    public class DesktopStrings
    {
        // Navigation & Titles
        public string HomeFeed { get; set; } = "Home Feed";
        public string Subscriptions { get; set; } = "Subscriptions";
        public string Trending { get; set; } = "Trending";
        public string Favorites { get; set; } = "Favorites";
        public string WatchLater { get; set; } = "Watch Later";
        public string History { get; set; } = "History";
        public string RecommendedFeed { get; set; } = "Recommended Feed";
        public string SearchPlaceholder { get; set; } = "Search videos, channels, topics...";
        public string SignIn { get; set; } = "Sign In";
        public string LanguageTitle { get; set; } = "App Language";
        public string TryOtherProducts { get; set; } = "Try Our Other Products ✨";

        // Filter Labels
        public string DateLabel { get; set; } = "Date:";
        public string LengthLabel { get; set; } = "Length:";
        public string SortLabel { get; set; } = "Sort:";
        public string ApplyFilters { get; set; } = "Apply Filters";
        public string Reset { get; set; } = "Reset";

        // Date Filter Options
        public string DateAnyTime { get; set; } = "Any Time";
        public string DateLastHour { get; set; } = "Last Hour";
        public string DateToday { get; set; } = "Today";
        public string DateThisWeek { get; set; } = "This Week";
        public string DateThisMonth { get; set; } = "This Month";

        // Duration Filter Options
        public string DurationAny { get; set; } = "Any Duration";
        public string DurationShort { get; set; } = "< 4 min (Short)";
        public string DurationMedium { get; set; } = "4 - 20 min (Med)";
        public string DurationLong { get; set; } = "> 20 min (Long)";

        // Sort Filter Options
        public string SortRelevance { get; set; } = "Relevance";
        public string SortNewest { get; set; } = "Newest First";
        public string SortMostViewed { get; set; } = "Most Viewed";

        // Subscriptions & Channels
        public string Subscribe { get; set; } = "Subscribe";
        public string Subscribed { get; set; } = "Subscribed";
        public string ManageSubscriptions { get; set; } = "Manage Subscriptions";
        public string ManageSubscriptionsHeading { get; set; } = "⚙️ Manage Your Subscribed Channels";
        public string AddChannel { get; set; } = "Add Channel / Creator";
        public string AddChannelDesc { get; set; } = "Enter any YouTube channel name or handle to add to your custom subscriptions feed:";
        public string NoSubscriptionsYet { get; set; } = "No subscribed channels yet. Click '➕' in the sidebar to add your favorites!";
        public string ResetToDefault { get; set; } = "Reset to Default";
        public string ClearAll { get; set; } = "Clear All";
        public string Cancel { get; set; } = "Cancel";
        public string Close { get; set; } = "Close";
        public string Add { get; set; } = "Add";

        // Folder Chips
        public string FolderAll { get; set; } = "All";
        public string FolderAi { get; set; } = "AI & Tech";
        public string FolderPodcasts { get; set; } = "Podcasts";
        public string FolderGaming { get; set; } = "Gaming";
        public string FolderMusic { get; set; } = "Music";

        // Feed Header Titles
        public string FeedAllVideos { get; set; } = "All Videos";
        public string FeedSubscriptions { get; set; } = "Subscriptions Feed";
        public string FeedTrending { get; set; } = "Trending Videos";
        public string FeedFavorites { get; set; } = "Favorite Videos";
        public string FeedWatchLater { get; set; } = "Watch Later Queue";
        public string FeedHistory { get; set; } = "Watch History";

        // Action Buttons & Context Menu
        public string Download { get; set; } = "Download";
        public string Downloading { get; set; } = "Downloading...";
        public string Downloaded { get; set; } = "Downloaded";
        public string Delete { get; set; } = "Delete";
        public string AddToPlaylist { get; set; } = "Add to Playlist";
        public string PlayNext { get; set; } = "Play Next";
        public string CopyLink { get; set; } = "Copy Link";
        public string OpenInBrowser { get; set; } = "Open in Browser";
        public string ClearHistory { get; set; } = "Clear History";
        public string SaveDestination { get; set; } = "Save Destination";
        public string ChangeFolder { get; set; } = "Change";
        public string OpenFolder { get; set; } = "Open ↗";

        // Player Controls & Overlays
        public string Play { get; set; } = "Play";
        public string Pause { get; set; } = "Pause";
        public string Mute { get; set; } = "Mute";
        public string Unmute { get; set; } = "Unmute";
        public string Quality { get; set; } = "Quality";
        public string Speed { get; set; } = "Speed";
        public string Subtitles { get; set; } = "Subtitles";
        public string Loop { get; set; } = "Loop";
        public string Fullscreen { get; set; } = "Fullscreen";
        public string MiniPlayer { get; set; } = "Mini Player";
        public string CinemaMode { get; set; } = "Cinema Mode";

        // Sleep Timer
        public string SleepTimer { get; set; } = "Sleep Timer 🌙";
        public string SleepTimerOff { get; set; } = "Off";
        public string SleepTimerActive { get; set; } = "Sleep Timer Active";
        public string ResumedFor { get; set; } = "Resumed for";

        // Status & Messages
        public string Loading { get; set; } = "Loading...";
        public string NoVideosFound { get; set; } = "No videos found";
        public string Retry { get; set; } = "Retry";
        public string OfflineReady { get; set; } = "✓ Ready for Offline";
    }

    public static class DesktopLocalizationService
    {
        public static string CurrentLanguageCode { get; set; } = "EN";

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
            { "EN", new DesktopStrings() },
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
                    LanguageTitle = "Idioma",
                    TryOtherProducts = "Prueba Nuestros Otros Productos ✨",

                    DateLabel = "Fecha:",
                    LengthLabel = "Duración:",
                    SortLabel = "Orden:",
                    ApplyFilters = "Aplicar Filtros",
                    Reset = "Restablecer",

                    DateAnyTime = "Cualquier fecha",
                    DateLastHour = "Última hora",
                    DateToday = "Hoy",
                    DateThisWeek = "Esta semana",
                    DateThisMonth = "Este mes",

                    DurationAny = "Cualquier duración",
                    DurationShort = "< 4 min (Corto)",
                    DurationMedium = "4 - 20 min (Medio)",
                    DurationLong = "> 20 min (Largo)",

                    SortRelevance = "Relevancia",
                    SortNewest = "Más recientes",
                    SortMostViewed = "Más vistos",

                    Subscribe = "Suscribirse",
                    Subscribed = "Suscrito",
                    ManageSubscriptions = "Gestionar Suscripciones",
                    ManageSubscriptionsHeading = "⚙️ Gestionar tus canales suscritos",
                    AddChannel = "Añadir Canal",
                    AddChannelDesc = "Introduce el nombre o handle del canal de YouTube:",
                    NoSubscriptionsYet = "Aún no hay canales suscritos. ¡Haz clic en '➕' en la barra lateral para añadir!",
                    ResetToDefault = "Restablecer Predeterminados",
                    ClearAll = "Borrar Todo",
                    Cancel = "Cancelar",
                    Close = "Cerrar",
                    Add = "Añadir",

                    FolderAll = "Todos",
                    FolderAi = "IA y Tech",
                    FolderPodcasts = "Pódcasts",
                    FolderGaming = "Videojuegos",
                    FolderMusic = "Música",

                    FeedAllVideos = "Todos los Videos",
                    FeedSubscriptions = "Feed de Suscripciones",
                    FeedTrending = "Videos en Tendencia",
                    FeedFavorites = "Videos Favoritos",
                    FeedWatchLater = "Cola de Ver Más Tarde",
                    FeedHistory = "Historial de Reproducción",

                    Download = "Descargar",
                    Downloading = "Descargando...",
                    Downloaded = "Descargado",
                    Delete = "Eliminar",
                    AddToPlaylist = "Añadir a lista",
                    PlayNext = "Reproducir siguiente",
                    CopyLink = "Copiar enlace",
                    OpenInBrowser = "Abrir en navegador",
                    ClearHistory = "Borrar historial",
                    SaveDestination = "Destino de Guardado",
                    ChangeFolder = "Cambiar",
                    OpenFolder = "Abrir ↗",

                    Play = "Reproducir",
                    Pause = "Pausar",
                    Mute = "Silenciar",
                    Unmute = "Activar sonido",
                    Quality = "Calidad",
                    Speed = "Velocidad",
                    Subtitles = "Subtítulos",
                    Loop = "Bucle",
                    Fullscreen = "Pantalla completa",
                    MiniPlayer = "Minirreproductor",
                    CinemaMode = "Modo cine",

                    SleepTimer = "Temporizador 🌙",
                    SleepTimerOff = "Desactivado",
                    SleepTimerActive = "Temporizador Activo",
                    ResumedFor = "Reanudado por",

                    Loading = "Cargando...",
                    NoVideosFound = "No se encontraron videos",
                    Retry = "Reintentar",
                    OfflineReady = "✓ Listo sin conexión"
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
                    LanguageTitle = "Langue",
                    TryOtherProducts = "Essayez Nos Autres Produits ✨",

                    DateLabel = "Date:",
                    LengthLabel = "Durée:",
                    SortLabel = "Trier:",
                    ApplyFilters = "Appliquer Filtres",
                    Reset = "Réinitialiser",

                    DateAnyTime = "Toute date",
                    DateLastHour = "Dernière heure",
                    DateToday = "Aujourd'hui",
                    DateThisWeek = "Cette semaine",
                    DateThisMonth = "Ce mois-ci",

                    DurationAny = "Toute durée",
                    DurationShort = "< 4 min (Court)",
                    DurationMedium = "4 - 20 min (Moyen)",
                    DurationLong = "> 20 min (Long)",

                    SortRelevance = "Pertinence",
                    SortNewest = "Plus récents",
                    SortMostViewed = "Plus vues",

                    Subscribe = "S'abonner",
                    Subscribed = "Abonné",
                    ManageSubscriptions = "Gérer les Abonnements",
                    ManageSubscriptionsHeading = "⚙️ Gérer vos chaînes abonnées",
                    AddChannel = "Ajouter une chaîne",
                    AddChannelDesc = "Entrez le nom ou le handle de la chaîne YouTube :",
                    NoSubscriptionsYet = "Aucune chaîne abonnée pour le moment. Cliquez sur '➕' pour en ajouter !",
                    ResetToDefault = "Réinitialiser par défaut",
                    ClearAll = "Tout effacer",
                    Cancel = "Annuler",
                    Close = "Fermer",
                    Add = "Ajouter",

                    FolderAll = "Tout",
                    FolderAi = "IA & Tech",
                    FolderPodcasts = "Podcasts",
                    FolderGaming = "Jeux Vidéo",
                    FolderMusic = "Musique",

                    FeedAllVideos = "Toutes les vidéos",
                    FeedSubscriptions = "Flux d'Abonnements",
                    FeedTrending = "Vidéos Tendances",
                    FeedFavorites = "Vidéos Favorites",
                    FeedWatchLater = "À regarder plus tard",
                    FeedHistory = "Historique de visionnage",

                    Download = "Télécharger",
                    Downloading = "Téléchargement...",
                    Downloaded = "Téléchargé",
                    Delete = "Supprimer",
                    AddToPlaylist = "Ajouter à la playlist",
                    PlayNext = "Lire ensuite",
                    CopyLink = "Copier le lien",
                    OpenInBrowser = "Ouvrir dans le navigateur",
                    ClearHistory = "Effacer l'historique",
                    SaveDestination = "Dossier d'enregistrement",
                    ChangeFolder = "Modifier",
                    OpenFolder = "Ouvrir ↗",

                    Play = "Lecture",
                    Pause = "Pause",
                    Mute = "Couper le son",
                    Unmute = "Activer le son",
                    Quality = "Qualité",
                    Speed = "Vitesse",
                    Subtitles = "Sous-titres",
                    Loop = "Boucle",
                    Fullscreen = "Plein écran",
                    MiniPlayer = "Mini-lecteur",
                    CinemaMode = "Mode cinéma",

                    SleepTimer = "Minuterie de veille 🌙",
                    SleepTimerOff = "Désactivé",
                    SleepTimerActive = "Minuterie Active",
                    ResumedFor = "Repris pour",

                    Loading = "Chargement...",
                    NoVideosFound = "Aucune vidéo trouvée",
                    Retry = "Réessayer",
                    OfflineReady = "✓ Disponible hors-ligne"
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
                    LanguageTitle = "Sprache",
                    TryOtherProducts = "Entdecke Unsere Weiteren Produkte ✨",

                    DateLabel = "Datum:",
                    LengthLabel = "Dauer:",
                    SortLabel = "Sortieren:",
                    ApplyFilters = "Filter anwenden",
                    Reset = "Zurücksetzen",

                    DateAnyTime = "Jederzeit",
                    DateLastHour = "Letzte Stunde",
                    DateToday = "Heute",
                    DateThisWeek = "Diese Woche",
                    DateThisMonth = "Diesen Monat",

                    DurationAny = "Beliebige Dauer",
                    DurationShort = "< 4 Min. (Kurz)",
                    DurationMedium = "4 - 20 Min. (Mittel)",
                    DurationLong = "> 20 Min. (Lang)",

                    SortRelevance = "Relevanz",
                    SortNewest = "Neueste zuerst",
                    SortMostViewed = "Meistgesehen",

                    Subscribe = "Abonnieren",
                    Subscribed = "Abonniert",
                    ManageSubscriptions = "Abos verwalten",
                    ManageSubscriptionsHeading = "⚙️ Verwalte deine abonnierten Kanäle",
                    AddChannel = "Kanal hinzufügen",
                    AddChannelDesc = "Gib einen YouTube-Kanalnamen oder Handle ein:",
                    NoSubscriptionsYet = "Noch keine Kanäle abonniert. Klicke auf '➕', um Favoriten hinzuzufügen!",
                    ResetToDefault = "Standard wiederherstellen",
                    ClearAll = "Alles löschen",
                    Cancel = "Abbrechen",
                    Close = "Schließen",
                    Add = "Hinzufügen",

                    FolderAll = "Alle",
                    FolderAi = "KI & Tech",
                    FolderPodcasts = "Podcasts",
                    FolderGaming = "Gaming",
                    FolderMusic = "Musik",

                    FeedAllVideos = "Alle Videos",
                    FeedSubscriptions = "Abonnement-Feed",
                    FeedTrending = "Trends Videos",
                    FeedFavorites = "Favoriten Videos",
                    FeedWatchLater = "Später ansehen",
                    FeedHistory = "Wiedergabeverlauf",

                    Download = "Herunterladen",
                    Downloading = "Wird geladen...",
                    Downloaded = "Heruntergeladen",
                    Delete = "Löschen",
                    AddToPlaylist = "Zur Playlist hinzufügen",
                    PlayNext = "Als Nächstes abspielen",
                    CopyLink = "Link kopieren",
                    OpenInBrowser = "Im Browser öffnen",
                    ClearHistory = "Verlauf löschen",
                    SaveDestination = "Speicherort",
                    ChangeFolder = "Ändern",
                    OpenFolder = "Öffnen ↗",

                    Play = "Abspielen",
                    Pause = "Pause",
                    Mute = "Stummschalten",
                    Unmute = "Ton an",
                    Quality = "Qualität",
                    Speed = "Geschwindigkeit",
                    Subtitles = "Untertitel",
                    Loop = "Wiederholen",
                    Fullscreen = "Vollbild",
                    MiniPlayer = "Miniplayer",
                    CinemaMode = "Kinomodus",

                    SleepTimer = "Sleeptimer 🌙",
                    SleepTimerOff = "Aus",
                    SleepTimerActive = "Sleeptimer Aktiv",
                    ResumedFor = "Fortgesetzt für",

                    Loading = "Wird geladen...",
                    NoVideosFound = "Keine Videos gefunden",
                    Retry = "Wiederholen",
                    OfflineReady = "✓ Offline verfügbar"
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
                    LanguageTitle = "Idioma",
                    TryOtherProducts = "Experimente Nossos Outros Produtos ✨",

                    DateLabel = "Data:",
                    LengthLabel = "Duração:",
                    SortLabel = "Ordenar:",
                    ApplyFilters = "Aplicar Filtros",
                    Reset = "Redefinir",

                    DateAnyTime = "Qualquer data",
                    DateLastHour = "Última hora",
                    DateToday = "Hoje",
                    DateThisWeek = "Esta semana",
                    DateThisMonth = "Este mês",

                    DurationAny = "Qualquer duração",
                    DurationShort = "< 4 min (Curto)",
                    DurationMedium = "4 - 20 min (Médio)",
                    DurationLong = "> 20 min (Longo)",

                    SortRelevance = "Relevância",
                    SortNewest = "Mais recentes",
                    SortMostViewed = "Mais visualizados",

                    Subscribe = "Inscrever-se",
                    Subscribed = "Inscrito",
                    ManageSubscriptions = "Gerenciar Inscrições",
                    ManageSubscriptionsHeading = "⚙️ Gerenciar seus canais inscritos",
                    AddChannel = "Adicionar Canal",
                    AddChannelDesc = "Insira o nome ou identificador do canal do YouTube:",
                    NoSubscriptionsYet = "Nenhum canal inscrito ainda. Clique em '➕' para adicionar!",
                    ResetToDefault = "Restaurar Padrão",
                    ClearAll = "Limpar Tudo",
                    Cancel = "Cancelar",
                    Close = "Fechar",
                    Add = "Adicionar",

                    FolderAll = "Todos",
                    FolderAi = "IA & Tech",
                    FolderPodcasts = "Podcasts",
                    FolderGaming = "Jogos",
                    FolderMusic = "Música",

                    FeedAllVideos = "Todos os Vídeos",
                    FeedSubscriptions = "Feed de Inscrições",
                    FeedTrending = "Vídeos em Alta",
                    FeedFavorites = "Vídeos Favoritos",
                    FeedWatchLater = "Assistir Mais Tarde",
                    FeedHistory = "Histórico de Exibição",

                    Download = "Baixar",
                    Downloading = "Baixando...",
                    Downloaded = "Baixado",
                    Delete = "Excluir",
                    AddToPlaylist = "Adicionar à playlist",
                    PlayNext = "Tocar a seguir",
                    CopyLink = "Copiar link",
                    OpenInBrowser = "Abrir no navegador",
                    ClearHistory = "Limpar histórico",
                    SaveDestination = "Destino de Salvamento",
                    ChangeFolder = "Alterar",
                    OpenFolder = "Abrir ↗",

                    Play = "Reproduzir",
                    Pause = "Pausar",
                    Mute = "Silenciar",
                    Unmute = "Ativar som",
                    Quality = "Qualidade",
                    Speed = "Velocidade",
                    Subtitles = "Legendas",
                    Loop = "Repetir",
                    Fullscreen = "Tela cheia",
                    MiniPlayer = "Miniplayer",
                    CinemaMode = "Modo cinema",

                    SleepTimer = "Temporizador 🌙",
                    SleepTimerOff = "Desativado",
                    SleepTimerActive = "Temporizador Ativo",
                    ResumedFor = "Retomado por",

                    Loading = "Carregando...",
                    NoVideosFound = "Nenhum vídeo encontrado",
                    Retry = "Tentar novamente",
                    OfflineReady = "✓ Pronto para offline"
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
                    LanguageTitle = "Lingua",
                    TryOtherProducts = "Prova i Nostri Altri Prodotti ✨",

                    DateLabel = "Data:",
                    LengthLabel = "Durata:",
                    SortLabel = "Ordina:",
                    ApplyFilters = "Applica Filtri",
                    Reset = "Reimposta",

                    DateAnyTime = "Qualsiasi data",
                    DateLastHour = "Ultima ora",
                    DateToday = "Oggi",
                    DateThisWeek = "Questa settimana",
                    DateThisMonth = "Questo mese",

                    DurationAny = "Qualsiasi durata",
                    DurationShort = "< 4 min (Breve)",
                    DurationMedium = "4 - 20 min (Medio)",
                    DurationLong = "> 20 min (Lungo)",

                    SortRelevance = "Rilevanza",
                    SortNewest = "Più recenti",
                    SortMostViewed = "Più visualizzati",

                    Subscribe = "Iscriviti",
                    Subscribed = "Iscritto",
                    ManageSubscriptions = "Gestisci Iscrizioni",
                    ManageSubscriptionsHeading = "⚙️ Gestisci i tuoi canali iscritti",
                    AddChannel = "Aggiungi Canale",
                    AddChannelDesc = "Inserisci il nome o handle del canale YouTube:",
                    NoSubscriptionsYet = "Nessun canale iscritto. Clicca su '➕' per aggiungerne uno!",
                    ResetToDefault = "Ripristina Predefiniti",
                    ClearAll = "Cancella Tutto",
                    Cancel = "Annulla",
                    Close = "Chiudi",
                    Add = "Aggiungi",

                    FolderAll = "Tutti",
                    FolderAi = "IA & Tech",
                    FolderPodcasts = "Podcast",
                    FolderGaming = "Gaming",
                    FolderMusic = "Musica",

                    FeedAllVideos = "Tutti i Video",
                    FeedSubscriptions = "Feed Iscrizioni",
                    FeedTrending = "Video di Tendenza",
                    FeedFavorites = "Video Preferiti",
                    FeedWatchLater = "Guarda Più Tardi",
                    FeedHistory = "Cronologia Visioni",

                    Download = "Scarica",
                    Downloading = "Download in corso...",
                    Downloaded = "Scaricato",
                    Delete = "Elimina",
                    AddToPlaylist = "Aggiungi a playlist",
                    PlayNext = "Riproduci successivo",
                    CopyLink = "Copia link",
                    OpenInBrowser = "Apri nel browser",
                    ClearHistory = "Cancella cronologia",
                    SaveDestination = "Cartella di Salvataggio",
                    ChangeFolder = "Modifica",
                    OpenFolder = "Apri ↗",

                    Play = "Riproduci",
                    Pause = "Pausa",
                    Mute = "Disattiva audio",
                    Unmute = "Attiva audio",
                    Quality = "Qualità",
                    Speed = "Velocità",
                    Subtitles = "Sottotitoli",
                    Loop = "Ripeti",
                    Fullscreen = "Schermo intero",
                    MiniPlayer = "Mini player",
                    CinemaMode = "Modalità cinema",

                    SleepTimer = "Timer di spegnimento 🌙",
                    SleepTimerOff = "Disattivato",
                    SleepTimerActive = "Timer Attivo",
                    ResumedFor = "Ripreso per",

                    Loading = "Caricamento...",
                    NoVideosFound = "Nessun video trovato",
                    Retry = "Riprova",
                    OfflineReady = "✓ Pronto offline"
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
                    LanguageTitle = "Язык",
                    TryOtherProducts = "Попробуйте другие наши продукты ✨",

                    DateLabel = "Дата:",
                    LengthLabel = "Длина:",
                    SortLabel = "Сортировка:",
                    ApplyFilters = "Применить",
                    Reset = "Сброс",

                    DateAnyTime = "За все время",
                    DateLastHour = "За последний час",
                    DateToday = "Сегодня",
                    DateThisWeek = "На этой неделе",
                    DateThisMonth = "В этом месяце",

                    DurationAny = "Любая длительность",
                    DurationShort = "< 4 мин (Короткие)",
                    DurationMedium = "4 - 20 мин (Средние)",
                    DurationLong = "> 20 мин (Длинные)",

                    SortRelevance = "По релевантности",
                    SortNewest = "Сначала новые",
                    SortMostViewed = "По просмотрам",

                    Subscribe = "Подписаться",
                    Subscribed = "Вы подписаны",
                    ManageSubscriptions = "Управление подписками",
                    ManageSubscriptionsHeading = "⚙️ Управление вашими подписками",
                    AddChannel = "Добавить канал",
                    AddChannelDesc = "Введите имя или тег канала YouTube:",
                    NoSubscriptionsYet = "Вы еще не подписаны ни на один канал. Нажмите '➕' для добавления!",
                    ResetToDefault = "По умолчанию",
                    ClearAll = "Очистить все",
                    Cancel = "Отмена",
                    Close = "Закрыть",
                    Add = "Добавить",

                    FolderAll = "Все",
                    FolderAi = "ИИ и Tech",
                    FolderPodcasts = "Подкасты",
                    FolderGaming = "Игры",
                    FolderMusic = "Музыка",

                    FeedAllVideos = "Все видео",
                    FeedSubscriptions = "Лента подписок",
                    FeedTrending = "В тренде",
                    FeedFavorites = "Избранные видео",
                    FeedWatchLater = "Смотреть позже",
                    FeedHistory = "История просмотров",

                    Download = "Скачать",
                    Downloading = "Скачивание...",
                    Downloaded = "Скачано",
                    Delete = "Удалить",
                    AddToPlaylist = "В плейлист",
                    PlayNext = "Следующее",
                    CopyLink = "Скопировать ссылку",
                    OpenInBrowser = "Открыть в браузере",
                    ClearHistory = "Очистить историю",
                    SaveDestination = "Папка сохранения",
                    ChangeFolder = "Изменить",
                    OpenFolder = "Открыть ↗",

                    Play = "Воспроизвести",
                    Pause = "Пауза",
                    Mute = "Без звука",
                    Unmute = "Включить звук",
                    Quality = "Качество",
                    Speed = "Скорость",
                    Subtitles = "Субтитры",
                    Loop = "Повтор",
                    Fullscreen = "Полный экран",
                    MiniPlayer = "Мини-плеер",
                    CinemaMode = "Режим кинотеатра",

                    SleepTimer = "Таймер сна 🌙",
                    SleepTimerOff = "Выкл",
                    SleepTimerActive = "Таймер сна активен",
                    ResumedFor = "Возобновлено на",

                    Loading = "Загрузка...",
                    NoVideosFound = "Видео не найдены",
                    Retry = "Повторить",
                    OfflineReady = "✓ Доступно офлайн"
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
                    LanguageTitle = "言語",
                    TryOtherProducts = "他の製品も試してみる ✨",

                    DateLabel = "日付:",
                    LengthLabel = "長さ:",
                    SortLabel = "並び替え:",
                    ApplyFilters = "フィルター適用",
                    Reset = "リセット",

                    DateAnyTime = "指定なし",
                    DateLastHour = "1時間以内",
                    DateToday = "今日",
                    DateThisWeek = "今週",
                    DateThisMonth = "今月",

                    DurationAny = "すべての長さ",
                    DurationShort = "4分未満 (ショート)",
                    DurationMedium = "4〜20分 (標準)",
                    DurationLong = "20分以上 (長編)",

                    SortRelevance = "関連度順",
                    SortNewest = "最新順",
                    SortMostViewed = "視聴回数順",

                    Subscribe = "チャンネル登録",
                    Subscribed = "登録済み",
                    ManageSubscriptions = "登録チャンネルの管理",
                    ManageSubscriptionsHeading = "⚙️ 登録チャンネルの管理",
                    AddChannel = "チャンネルを追加",
                    AddChannelDesc = "YouTubeチャンネル名またはハンドルを入力してください:",
                    NoSubscriptionsYet = "登録されたチャンネルはまだありません。「➕」をクリックして追加してください！",
                    ResetToDefault = "初期設定に戻す",
                    ClearAll = "すべてクリア",
                    Cancel = "キャンセル",
                    Close = "閉じる",
                    Add = "追加",

                    FolderAll = "すべて",
                    FolderAi = "AI・テクノロジー",
                    FolderPodcasts = "ポッドキャスト",
                    FolderGaming = "ゲーム",
                    FolderMusic = "音楽",

                    FeedAllVideos = "すべての動画",
                    FeedSubscriptions = "登録チャンネルのフィード",
                    FeedTrending = "急上昇動画",
                    FeedFavorites = "お気に入り動画",
                    FeedWatchLater = "後で見る動画",
                    FeedHistory = "再生履歴",

                    Download = "ダウンロード",
                    Downloading = "ダウンロード中...",
                    Downloaded = "ダウンロード済み",
                    Delete = "削除",
                    AddToPlaylist = "再生リストに追加",
                    PlayNext = "次に再生",
                    CopyLink = "リンクをコピー",
                    OpenInBrowser = "ブラウザで開く",
                    ClearHistory = "履歴を削除",
                    SaveDestination = "保存先フォルダ",
                    ChangeFolder = "変更",
                    OpenFolder = "開く ↗",

                    Play = "再生",
                    Pause = "一時停止",
                    Mute = "ミュート",
                    Unmute = "ミュート解除",
                    Quality = "画質",
                    Speed = "速度",
                    Subtitles = "字幕",
                    Loop = "ループ再生",
                    Fullscreen = "全画面",
                    MiniPlayer = "ミニプレーヤー",
                    CinemaMode = "シアターモード",

                    SleepTimer = "スリープタイマー 🌙",
                    SleepTimerOff = "オフ",
                    SleepTimerActive = "タイマー作動中",
                    ResumedFor = "再開:",

                    Loading = "読み込み中...",
                    NoVideosFound = "動画が見つかりません",
                    Retry = "再試行",
                    OfflineReady = "✓ オフライン再生可能"
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
                    LanguageTitle = "언어",
                    TryOtherProducts = "다른 추천 앱 사용해보기 ✨",

                    DateLabel = "업로드 날짜:",
                    LengthLabel = "재생 시간:",
                    SortLabel = "정렬 기준:",
                    ApplyFilters = "필터 적용",
                    Reset = "초기화",

                    DateAnyTime = "전체 기간",
                    DateLastHour = "지난 1시간",
                    DateToday = "오늘",
                    DateThisWeek = "이번 주",
                    DateThisMonth = "이번 달",

                    DurationAny = "모든 길이",
                    DurationShort = "4분 미만 (짧은 영상)",
                    DurationMedium = "4 - 20분 (중간)",
                    DurationLong = "20분 이상 (긴 영상)",

                    SortRelevance = "관련성 높은순",
                    SortNewest = "최신순",
                    SortMostViewed = "조회수순",

                    Subscribe = "구독",
                    Subscribed = "구독중",
                    ManageSubscriptions = "구독 관리",
                    ManageSubscriptionsHeading = "⚙️ 구독한 채널 관리",
                    AddChannel = "채널 추가",
                    AddChannelDesc = "추가할 유튜브 채널 이름이나 핸들을 입력하세요:",
                    NoSubscriptionsYet = "아직 구독한 채널이 없습니다. 사이드바의 '➕' 버튼을 눌러 추가해보세요!",
                    ResetToDefault = "기본값으로 초기화",
                    ClearAll = "전체 삭제",
                    Cancel = "취소",
                    Close = "닫기",
                    Add = "추가",

                    FolderAll = "전체",
                    FolderAi = "AI 및 기술",
                    FolderPodcasts = "팟캐스트",
                    FolderGaming = "게임",
                    FolderMusic = "음악",

                    FeedAllVideos = "모든 동영상",
                    FeedSubscriptions = "구독 채널 피드",
                    FeedTrending = "인기 급상승 동영상",
                    FeedFavorites = "좋아요한 동영상",
                    FeedWatchLater = "나중에 볼 동영상",
                    FeedHistory = "시청 기록",

                    Download = "다운로드",
                    Downloading = "다운로드 중...",
                    Downloaded = "다운로드 완료",
                    Delete = "삭제",
                    AddToPlaylist = "재생목록에 추가",
                    PlayNext = "다음에 재생",
                    CopyLink = "링크 복사",
                    OpenInBrowser = "브라우저에서 열기",
                    ClearHistory = "시청 기록 지우기",
                    SaveDestination = "저장 위치",
                    ChangeFolder = "변경",
                    OpenFolder = "열기 ↗",

                    Play = "재생",
                    Pause = "일시정지",
                    Mute = "음소거",
                    Unmute = "음소거 해제",
                    Quality = "화질",
                    Speed = "속도",
                    Subtitles = "자막",
                    Loop = "반복 재생",
                    Fullscreen = "전체 화면",
                    MiniPlayer = "미니 플레이어",
                    CinemaMode = "영화관 모드",

                    SleepTimer = "수면 타이머 🌙",
                    SleepTimerOff = "끄기",
                    SleepTimerActive = "수면 타이머 작동 중",
                    ResumedFor = "재개 시간:",

                    Loading = "로딩 중...",
                    NoVideosFound = "동영상을 찾을 수 없습니다",
                    Retry = "다시 시도",
                    OfflineReady = "✓ 오프라인 저장됨"
                }
            },
            {
                "ZH", new DesktopStrings
                {
                    HomeFeed = "首页推荐",
                    Subscriptions = "订阅频道",
                    Trending = "时下流行",
                    Favorites = "我的收藏",
                    WatchLater = "稍后观看",
                    History = "播放历史",
                    RecommendedFeed = "为你推荐",
                    SearchPlaceholder = "搜索视频、频道、话题...",
                    SignIn = "登录账号",
                    LanguageTitle = "应用语言",
                    TryOtherProducts = "探索我们的其他产品 ✨",

                    DateLabel = "上传时间:",
                    LengthLabel = "时长范围:",
                    SortLabel = "排序方式:",
                    ApplyFilters = "应用筛选",
                    Reset = "重置条件",

                    DateAnyTime = "不限时间",
                    DateLastHour = "过去1小时内",
                    DateToday = "今天",
                    DateThisWeek = "本周",
                    DateThisMonth = "本月",

                    DurationAny = "不限时长",
                    DurationShort = "< 4 分钟 (短视频)",
                    DurationMedium = "4 - 20 分钟 (中等)",
                    DurationLong = "> 20 分钟 (长视频)",

                    SortRelevance = "相关度最高",
                    SortNewest = "最新发布",
                    SortMostViewed = "播放量最多",

                    Subscribe = "订阅",
                    Subscribed = "已订阅",
                    ManageSubscriptions = "管理订阅",
                    ManageSubscriptionsHeading = "⚙️ 管理您订阅的频道",
                    AddChannel = "添加频道",
                    AddChannelDesc = "输入 YouTube 频道名称或标识符:",
                    NoSubscriptionsYet = "暂无订阅的频道。点击侧边栏的 '➕' 即可添加！",
                    ResetToDefault = "恢复默认频道",
                    ClearAll = "清空全部",
                    Cancel = "取消",
                    Close = "关闭",
                    Add = "添加",

                    FolderAll = "全部",
                    FolderAi = "AI 与科技",
                    FolderPodcasts = "播客与访谈",
                    FolderGaming = "游戏视频",
                    FolderMusic = "音乐旋律",

                    FeedAllVideos = "全部视频",
                    FeedSubscriptions = "订阅频道内容",
                    FeedTrending = "时下热门视频",
                    FeedFavorites = "我的收藏视频",
                    FeedWatchLater = "稍后观看队列",
                    FeedHistory = "观看历史记录",

                    Download = "下载视频",
                    Downloading = "正在下载...",
                    Downloaded = "已完成下载",
                    Delete = "删除",
                    AddToPlaylist = "添加到播放列表",
                    PlayNext = "接下来播放",
                    CopyLink = "复制链接",
                    OpenInBrowser = "在浏览器中打开",
                    ClearHistory = "清空历史记录",
                    SaveDestination = "视频保存路径",
                    ChangeFolder = "更改目录",
                    OpenFolder = "打开目录 ↗",

                    Play = "播放",
                    Pause = "暂停",
                    Mute = "静音",
                    Unmute = "取消静音",
                    Quality = "清晰度",
                    Speed = "播放速度",
                    Subtitles = "字幕",
                    Loop = "循环播放",
                    Fullscreen = "全屏播放",
                    MiniPlayer = "小窗播放",
                    CinemaMode = "剧场模式",

                    SleepTimer = "睡眠定时器 🌙",
                    SleepTimerOff = "关闭",
                    SleepTimerActive = "定时器已开启",
                    ResumedFor = "已恢复定时:",

                    Loading = "加载中...",
                    NoVideosFound = "未找到相关视频",
                    Retry = "重试",
                    OfflineReady = "✓ 支持离线播放"
                }
            },
            {
                "HI", new DesktopStrings
                {
                    HomeFeed = "मुख्य पृष्ठ",
                    Subscriptions = "सदस्यताएँ",
                    Trending = "ट्रेंडिंग",
                    Favorites = "पसंदीदा",
                    WatchLater = "बाद में देखें",
                    History = "इतिहास",
                    RecommendedFeed = "अनुशंसित फ़ीड",
                    SearchPlaceholder = "वीडियो, चैनल, विषय खोजें...",
                    SignIn = "साइन इन करें",
                    LanguageTitle = "भाषा",
                    TryOtherProducts = "हमारे अन्य उत्पाद आज़माएँ ✨",

                    DateLabel = "तारीख:",
                    LengthLabel = "अवधि:",
                    SortLabel = "क्रमबद्ध:",
                    ApplyFilters = "फ़िल्टर लागू करें",
                    Reset = "रीसेट करें",

                    DateAnyTime = "किसी भी समय",
                    DateLastHour = "पिछला घंटा",
                    DateToday = "आज",
                    DateThisWeek = "इस सप्ताह",
                    DateThisMonth = "इस महीने",

                    DurationAny = "कोई भी अवधि",
                    DurationShort = "< 4 मिनट (छोटा)",
                    DurationMedium = "4 - 20 मिनट (मध्यम)",
                    DurationLong = "> 20 मिनट (लंबा)",

                    SortRelevance = "प्रासंगिकता",
                    SortNewest = "नवीनतम पहले",
                    SortMostViewed = "सर्वाधिक देखे गए",

                    Subscribe = "सदस्यता लें",
                    Subscribed = "सदस्यता ली गई",
                    ManageSubscriptions = "सदस्यता प्रबंधित करें",
                    ManageSubscriptionsHeading = "⚙️ अपने सब्सक्राइब किए गए चैनल प्रबंधित करें",
                    AddChannel = "चैनल जोड़ें",
                    AddChannelDesc = "YouTube चैनल का नाम या हैंडल दर्ज करें:",
                    NoSubscriptionsYet = "अभी तक कोई चैनल सब्सक्राइब नहीं किया गया है। जोड़ने के लिए '➕' पर क्लिक करें!",
                    ResetToDefault = "डिफ़ॉल्ट पर रीसेट करें",
                    ClearAll = "सभी साफ़ करें",
                    Cancel = "रद्द करें",
                    Close = "बंद करें",
                    Add = "जोड़ें",

                    FolderAll = "सभी",
                    FolderAi = "एआई और टेक",
                    FolderPodcasts = "पॉडकास्ट",
                    FolderGaming = "गेमिंग",
                    FolderMusic = "संगीत",

                    FeedAllVideos = "सभी वीडियो",
                    FeedSubscriptions = "सदस्यता फ़ीड",
                    FeedTrending = "ट्रेंडिंग वीडियो",
                    FeedFavorites = "पसंदीदा वीडियो",
                    FeedWatchLater = "बाद में देखें",
                    FeedHistory = "देखने का इतिहास",

                    Download = "डाउनलोड",
                    Downloading = "डाउनलोड हो रहा है...",
                    Downloaded = "डाउनलोड किया गया",
                    Delete = "हटाएं",
                    AddToPlaylist = "प्लेलिस्ट में जोड़ें",
                    PlayNext = "अगला चलाएं",
                    CopyLink = "लिंक कॉपी करें",
                    OpenInBrowser = "ब्राउज़र में खोलें",
                    ClearHistory = "इतिहास साफ़ करें",
                    SaveDestination = "सहेजने का स्थान",
                    ChangeFolder = "बदलें",
                    OpenFolder = "खोलें ↗",

                    Play = "चलाएं",
                    Pause = "रोकें",
                    Mute = "म्यूट करें",
                    Unmute = "ध्वनि चालू करें",
                    Quality = "गुणवत्ता",
                    Speed = "गति",
                    Subtitles = "उपशीर्षक",
                    Loop = "लूप",
                    Fullscreen = "पूर्ण स्क्रीन",
                    MiniPlayer = "मिनी प्लेयर",
                    CinemaMode = "सिनेमा मोड",

                    SleepTimer = "स्लीप टाइमर 🌙",
                    SleepTimerOff = "बंद",
                    SleepTimerActive = "स्लीप टाइमर सक्रिय",
                    ResumedFor = "के लिए पुनः शुरू:",

                    Loading = "लोड हो रहा है...",
                    NoVideosFound = "कोई वीडियो नहीं मिला",
                    Retry = "पुनः प्रयास करें",
                    OfflineReady = "✓ ऑफ़लाइन के लिए तैयार"
                }
            },
            {
                "AR", new DesktopStrings
                {
                    HomeFeed = "الرئيسية",
                    Subscriptions = "الاشتراكات",
                    Trending = "المحتوى الرائج",
                    Favorites = "المفضلة",
                    WatchLater = "المشاهدة لاحقاً",
                    History = "السجل",
                    RecommendedFeed = "موصى به لك",
                    SearchPlaceholder = "ابحث عن مقاطع الفيديو، القنوات، المواضيع...",
                    SignIn = "تسجيل الدخول",
                    LanguageTitle = "اللغة",
                    TryOtherProducts = "جرّب منتجاتنا الأخرى ✨",

                    DateLabel = "التاريخ:",
                    LengthLabel = "المدة:",
                    SortLabel = "الترتيب:",
                    ApplyFilters = "تطبيق الفلاتر",
                    Reset = "إعادة ضبط",

                    DateAnyTime = "أي وقت",
                    DateLastHour = "آخر ساعة",
                    DateToday = "اليوم",
                    DateThisWeek = "هذا الأسبوع",
                    DateThisMonth = "هذا الشهر",

                    DurationAny = "أي مدة",
                    DurationShort = "< 4 دقائق (قصير)",
                    DurationMedium = "4 - 20 دقيقة (متوسط)",
                    DurationLong = "> 20 دقيقة (طويل)",

                    SortRelevance = "مدى الصلة",
                    SortNewest = "الأحدث أولاً",
                    SortMostViewed = "الأكثر مشاهدة",

                    Subscribe = "اشتراك",
                    Subscribed = "مشترك",
                    ManageSubscriptions = "إدارة الاشتراكات",
                    ManageSubscriptionsHeading = "⚙️ إدارة القنوات المشترك بها",
                    AddChannel = "إضافة قناة",
                    AddChannelDesc = "أدخل اسم أو معرّف قناة يوتيوب:",
                    NoSubscriptionsYet = "لا توجد قنوات مشترك بها بعد. انقر على '➕' للإضافة!",
                    ResetToDefault = "استعادة الافتراضي",
                    ClearAll = "مسح الكل",
                    Cancel = "إلغاء",
                    Close = "إغلاق",
                    Add = "إضافة",

                    FolderAll = "الكل",
                    FolderAi = "الذكاء الاصطناعي والتقنية",
                    FolderPodcasts = "البودكاست",
                    FolderGaming = "الألعاب",
                    FolderMusic = "الموسيقى",

                    FeedAllVideos = "جميع الفيديوهات",
                    FeedSubscriptions = "خلاصة الاشتراكات",
                    FeedTrending = "الفيديوهات الرائجة",
                    FeedFavorites = "الفيديوهات المفضلة",
                    FeedWatchLater = "المشاهدة لاحقاً",
                    FeedHistory = "سجل المشاهدة",

                    Download = "تنزيل",
                    Downloading = "جاري التنزيل...",
                    Downloaded = "تم التنزيل",
                    Delete = "حذف",
                    AddToPlaylist = "إضافة إلى قائمة التشغيل",
                    PlayNext = "تشغيل التالي",
                    CopyLink = "نسخ الرابط",
                    OpenInBrowser = "فتح في المتصفح",
                    ClearHistory = "مسح السجل",
                    SaveDestination = "مجلد الحفظ",
                    ChangeFolder = "تغيير",
                    OpenFolder = "فتح ↗",

                    Play = "تشغيل",
                    Pause = "إيقاف مؤقت",
                    Mute = "كتم الصوت",
                    Unmute = "إلغاء الكتم",
                    Quality = "الجودة",
                    Speed = "السرعة",
                    Subtitles = "الترجمة",
                    Loop = "تكرار",
                    Fullscreen = "ملء الشاشة",
                    MiniPlayer = "مشغل مصغر",
                    CinemaMode = "وضع السينما",

                    SleepTimer = "مؤقت النوم 🌙",
                    SleepTimerOff = "إيقاف",
                    SleepTimerActive = "مؤقت النوم نشط",
                    ResumedFor = "تم الاستئناف لمدة:",

                    Loading = "جاري التحميل...",
                    NoVideosFound = "لم يتم العثور على مقاطع فيديو",
                    Retry = "إعادة المحاولة",
                    OfflineReady = "✓ جاهز للمشاهدة بدون إنترنت"
                }
            }
        };

        public static DesktopStrings GetStrings(string? langCode = null)
        {
            var code = langCode ?? CurrentLanguageCode;
            if (Translations.TryGetValue(code, out var strings))
            {
                return strings;
            }
            return Translations["EN"];
        }

        public static string LocalizeRelativeTime(string? raw, string? langCode = null)
        {
            if (string.IsNullOrWhiteSpace(raw)) return "";
            var lang = (langCode ?? CurrentLanguageCode).ToUpperInvariant();
            if (lang == "EN") return raw;

            var lower = raw.Trim().ToLowerInvariant();
            bool isStreamed = lower.Contains("streamed") || lower.Contains("en vivo") || lower.Contains("direct");

            if (lower.Contains("just now") || lower.Contains("moments ago") || lower.Contains("now"))
            {
                return lang switch
                {
                    "ES" => "hace un momento",
                    "FR" => "à l'instant",
                    "DE" => "gerade eben",
                    "PT" => "agora mesmo",
                    "IT" => "proprio ora",
                    "RU" => "только что",
                    "JA" => "たった今",
                    "KO" => "방금 전",
                    "ZH" => "刚刚",
                    "HI" => "अभी-अभी",
                    "AR" => "الآن",
                    _ => raw
                };
            }

            if (lower.Contains("yesterday"))
            {
                return lang switch
                {
                    "ES" => "ayer",
                    "FR" => "hier",
                    "DE" => "gestern",
                    "PT" => "ontem",
                    "IT" => "ieri",
                    "RU" => "вчера",
                    "JA" => "昨日",
                    "KO" => "어제",
                    "ZH" => "昨天",
                    "HI" => "कल",
                    "AR" => "أمس",
                    _ => raw
                };
            }

            if (lower.Contains("today"))
            {
                return lang switch
                {
                    "ES" => "hoy",
                    "FR" => "aujourd'hui",
                    "DE" => "heute",
                    "PT" => "hoje",
                    "IT" => "oggi",
                    "RU" => "сегодня",
                    "JA" => "今日",
                    "KO" => "오늘",
                    "ZH" => "今天",
                    "HI" => "आज",
                    "AR" => "اليوم",
                    _ => raw
                };
            }

            var match = Regex.Match(lower, @"(\d+)\s*(second|sec|minute|min|hour|hr|day|week|month|year)s?\s*ago", RegexOptions.IgnoreCase);
            if (!match.Success)
            {
                match = Regex.Match(lower, @"(\d+)\s*(second|minute|hour|day|week|month|year)s?", RegexOptions.IgnoreCase);
            }

            if (match.Success)
            {
                if (long.TryParse(match.Groups[1].Value, out long num))
                {
                    var unit = match.Groups[2].Value.ToLowerInvariant();
                    string result = FormatRelativeUnit(num, unit, lang);
                    if (isStreamed)
                    {
                        string streamPrefix = lang switch
                        {
                            "ES" => "Emitido ",
                            "FR" => "Diffusé ",
                            "DE" => "Gestreamt ",
                            "PT" => "Transmitido ",
                            "IT" => "Trasmesso ",
                            "RU" => "Транслировалось ",
                            "JA" => "配信済み ",
                            "KO" => "스트리밍 완료: ",
                            "ZH" => "已直播 ",
                            "HI" => "लाइव स्ट्रीम ",
                            "AR" => "تم البث ",
                            _ => "Streamed "
                        };
                        return streamPrefix + result;
                    }
                    return result;
                }
            }

            return raw;
        }

        private static string FormatRelativeUnit(long num, string unit, string lang)
        {
            bool isSec = unit.StartsWith("sec");
            bool isMin = unit.StartsWith("min");
            bool isHour = unit.StartsWith("hour") || unit.StartsWith("hr");
            bool isDay = unit.StartsWith("day");
            bool isWeek = unit.StartsWith("week");
            bool isMonth = unit.StartsWith("month");
            bool isYear = unit.StartsWith("year");

            return lang switch
            {
                "ES" => isSec ? $"hace {num} segundo{(num != 1 ? "s" : "")}"
                      : isMin ? $"hace {num} minuto{(num != 1 ? "s" : "")}"
                      : isHour ? $"hace {num} hora{(num != 1 ? "s" : "")}"
                      : isDay ? (num == 1 ? "hace 1 día" : $"hace {num} días")
                      : isWeek ? $"hace {num} semana{(num != 1 ? "s" : "")}"
                      : isMonth ? (num == 1 ? "hace 1 mes" : $"hace {num} meses")
                      : isYear ? (num == 1 ? "hace 1 año" : $"hace {num} años")
                      : $"hace {num} {unit}",

                "FR" => isSec ? $"il y a {num} seconde{(num > 1 ? "s" : "")}"
                      : isMin ? $"il y a {num} minute{(num > 1 ? "s" : "")}"
                      : isHour ? $"il y a {num} heure{(num > 1 ? "s" : "")}"
                      : isDay ? $"il y a {num} jour{(num > 1 ? "s" : "")}"
                      : isWeek ? $"il y a {num} semaine{(num > 1 ? "s" : "")}"
                      : isMonth ? $"il y a {num} mois"
                      : isYear ? $"il y a {num} an{(num > 1 ? "s" : "")}"
                      : $"il y a {num} {unit}",

                "DE" => isSec ? $"vor {num} Sekunde{(num != 1 ? "n" : "")}"
                      : isMin ? $"vor {num} Minute{(num != 1 ? "n" : "")}"
                      : isHour ? $"vor {num} Stunde{(num != 1 ? "n" : "")}"
                      : isDay ? (num == 1 ? "vor 1 Tag" : $"vor {num} Tagen")
                      : isWeek ? $"vor {num} Woche{(num != 1 ? "n" : "")}"
                      : isMonth ? (num == 1 ? "vor 1 Monat" : $"vor {num} Monaten")
                      : isYear ? (num == 1 ? "vor 1 Jahr" : $"vor {num} Jahren")
                      : $"vor {num} {unit}",

                "PT" => isSec ? $"há {num} segundo{(num != 1 ? "s" : "")}"
                      : isMin ? $"há {num} minuto{(num != 1 ? "s" : "")}"
                      : isHour ? $"há {num} hora{(num != 1 ? "s" : "")}"
                      : isDay ? (num == 1 ? "há 1 dia" : $"há {num} dias")
                      : isWeek ? $"há {num} semana{(num != 1 ? "s" : "")}"
                      : isMonth ? (num == 1 ? "há 1 mês" : $"há {num} meses")
                      : isYear ? (num == 1 ? "há 1 ano" : $"há {num} anos")
                      : $"há {num} {unit}",

                "IT" => isSec ? $"{num} second{(num == 1 ? "o" : "i")} fa"
                      : isMin ? $"{num} minut{(num == 1 ? "o" : "i")} fa"
                      : isHour ? $"{num} or{(num == 1 ? "a" : "e")} fa"
                      : isDay ? $"{num} giorn{(num == 1 ? "o" : "i")} fa"
                      : isWeek ? $"{num} settiman{(num == 1 ? "a" : "e")} fa"
                      : isMonth ? $"{num} mes{(num == 1 ? "e" : "i")} fa"
                      : isYear ? $"{num} ann{(num == 1 ? "o" : "i")} fa"
                      : $"{num} {unit} fa",

                "RU" => isSec ? $"{num} сек. назад"
                      : isMin ? $"{num} мин. назад"
                      : isHour ? (num % 10 == 1 && num % 100 != 11 ? $"{num} час назад" : num % 10 >= 2 && num % 10 <= 4 && (num % 100 < 10 || num % 100 >= 20) ? $"{num} часа назад" : $"{num} часов назад")
                      : isDay ? (num % 10 == 1 && num % 100 != 11 ? $"{num} день назад" : num % 10 >= 2 && num % 10 <= 4 && (num % 100 < 10 || num % 100 >= 20) ? $"{num} дня назад" : $"{num} дней назад")
                      : isWeek ? (num % 10 == 1 && num % 100 != 11 ? $"{num} неделю назад" : num % 10 >= 2 && num % 10 <= 4 && (num % 100 < 10 || num % 100 >= 20) ? $"{num} недели назад" : $"{num} недель назад")
                      : isMonth ? (num % 10 == 1 && num % 100 != 11 ? $"{num} месяц назад" : num % 10 >= 2 && num % 10 <= 4 && (num % 100 < 10 || num % 100 >= 20) ? $"{num} месяца назад" : $"{num} месяцев назад")
                      : isYear ? (num % 10 == 1 && num % 100 != 11 ? $"{num} год назад" : num % 10 >= 2 && num % 10 <= 4 && (num % 100 < 10 || num % 100 >= 20) ? $"{num} года назад" : $"{num} лет назад")
                      : $"{num} назад",

                "JA" => isSec ? $"{num}秒前"
                      : isMin ? $"{num}分前"
                      : isHour ? $"{num}時間前"
                      : isDay ? $"{num}日前"
                      : isWeek ? $"{num}週間前"
                      : isMonth ? $"{num}か月前"
                      : isYear ? $"{num}年前"
                      : $"{num}前",

                "KO" => isSec ? $"{num}초 전"
                      : isMin ? $"{num}분 전"
                      : isHour ? $"{num}시간 전"
                      : isDay ? $"{num}일 전"
                      : isWeek ? $"{num}주 전"
                      : isMonth ? $"{num}개월 전"
                      : isYear ? $"{num}년 전"
                      : $"{num} 전",

                "ZH" => isSec ? $"{num} 秒前"
                      : isMin ? $"{num} 分钟前"
                      : isHour ? $"{num} 小时前"
                      : isDay ? $"{num} 天前"
                      : isWeek ? $"{num} 周前"
                      : isMonth ? $"{num} 个月前"
                      : isYear ? $"{num} 年前"
                      : $"{num} 前",

                "HI" => isSec ? $"{num} सेकंड पहले"
                      : isMin ? $"{num} मिनट पहले"
                      : isHour ? $"{num} घंटे पहले"
                      : isDay ? $"{num} दिन पहले"
                      : isWeek ? $"{num} सप्ताह पहले"
                      : isMonth ? $"{num} महीने पहले"
                      : isYear ? $"{num} साल पहले"
                      : $"{num} पहले",

                "AR" => isSec ? (num == 1 ? "منذ ثانية" : num == 2 ? "منذ ثانيتين" : num <= 10 ? $"منذ {num} ثوانٍ" : $"منذ {num} ثانية")
                      : isMin ? (num == 1 ? "منذ دقيقة" : num == 2 ? "منذ دقيقتين" : num <= 10 ? $"منذ {num} دقائق" : $"منذ {num} دقيقة")
                      : isHour ? (num == 1 ? "منذ ساعة" : num == 2 ? "منذ ساعتين" : num <= 10 ? $"منذ {num} ساعات" : $"منذ {num} ساعة")
                      : isDay ? (num == 1 ? "منذ يوم" : num == 2 ? "منذ يومين" : num <= 10 ? $"منذ {num} أيام" : $"منذ {num} يوم")
                      : isWeek ? (num == 1 ? "منذ أسبوع" : num == 2 ? "منذ أسبوعين" : num <= 10 ? $"منذ {num} أسابيع" : $"منذ {num} أسبوع")
                      : isMonth ? (num == 1 ? "منذ شهر" : num == 2 ? "منذ شهرين" : num <= 10 ? $"منذ {num} أشهر" : $"منذ {num} شهر")
                      : isYear ? (num == 1 ? "منذ سنة" : num == 2 ? "منذ سنتين" : num <= 10 ? $"منذ {num} سنوات" : $"منذ {num} سنة")
                      : $"منذ {num} {unit}",

                _ => $"{num} {unit} ago"
            };
        }

        public static string LocalizeViewCount(string? raw, string? langCode = null)
        {
            if (string.IsNullOrWhiteSpace(raw)) return "";
            var lang = (langCode ?? CurrentLanguageCode).ToUpperInvariant();
            if (lang == "EN") return raw;

            var lower = raw.Trim().ToLowerInvariant();

            if (lower.Contains("no views"))
            {
                return lang switch
                {
                    "ES" => "Sin vistas",
                    "FR" => "Aucune vue",
                    "DE" => "Keine Aufrufe",
                    "PT" => "Nenhuma visualização",
                    "IT" => "Nessuna visualizzazione",
                    "RU" => "Нет просмотров",
                    "JA" => "視聴回数なし",
                    "KO" => "조회수 없음",
                    "ZH" => "暂无观看",
                    "HI" => "कोई दृश्य नहीं",
                    "AR" => "بلا مشاهدات",
                    _ => raw
                };
            }

            var match = Regex.Match(lower, @"^([\d\.,]+(?:\s*[kmbt])?)\s*(?:views|view)?", RegexOptions.IgnoreCase);
            if (match.Success)
            {
                string countPart = match.Groups[1].Value.Trim().ToUpperInvariant();
                
                return lang switch
                {
                    "ES" => $"{countPart} visualizaciones",
                    "FR" => $"{countPart} vues",
                    "DE" => $"{countPart} Aufrufe",
                    "PT" => $"{countPart} visualizações",
                    "IT" => $"{countPart} visualizzazioni",
                    "RU" => $"{countPart} просмотров",
                    "JA" => $"{countPart} 回視聴",
                    "KO" => $"{countPart}회 조회",
                    "ZH" => $"{countPart} 次观看",
                    "HI" => $"{countPart} दृश्य",
                    "AR" => $"{countPart} مشاهدة",
                    _ => $"{countPart} views"
                };
            }

            return raw;
        }
    }
}
