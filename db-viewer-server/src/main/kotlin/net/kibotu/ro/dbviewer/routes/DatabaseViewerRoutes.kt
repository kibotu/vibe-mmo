package net.kibotu.ro.dbviewer.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import net.kibotu.ro.dbviewer.service.DatabaseViewerService
import net.kibotu.ro.dbviewer.service.ZoneServerService

fun Route.databaseViewerRoutes(
    databaseViewerService: DatabaseViewerService,
    zoneServerService: ZoneServerService
) {
    // API endpoints
    get("/api/stats") {
        try {
            val stats = databaseViewerService.getStats()
            call.respond(stats)
        } catch (e: Exception) {
            call.application.environment.log.error("Failed to load stats", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to load stats: ${e.message}")
            )
        }
    }
    
    // Diagnostic endpoint
    get("/api/diagnostic") {
        val accountDbFile = java.io.File("./data/accountdb.mv.db")
        val characterDbFile = java.io.File("./data/characterdb.mv.db")
        call.respond(mapOf(
            "accountDbExists" to accountDbFile.exists(),
            "accountDbPath" to accountDbFile.absolutePath,
            "accountDbSize" to if (accountDbFile.exists()) accountDbFile.length() else 0,
            "characterDbExists" to characterDbFile.exists(),
            "characterDbPath" to characterDbFile.absolutePath,
            "characterDbSize" to if (characterDbFile.exists()) characterDbFile.length() else 0,
            "message" to if (!accountDbFile.exists() || !characterDbFile.exists()) 
                "Database files don't exist. Account-server and Character-server need to be restarted to use file-based databases." 
            else "Database files exist."
        ))
    }
    
    get("/api/accounts") {
        try {
            val accounts = databaseViewerService.getAccounts()
            call.respond(accounts)
        } catch (e: Exception) {
            call.application.environment.log.error("Failed to load accounts", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to load accounts: ${e.message}")
            )
        }
    }
    
    get("/api/sessions") {
        try {
            val sessions = databaseViewerService.getSessions()
            call.respond(sessions)
        } catch (e: Exception) {
            call.application.environment.log.error("Failed to load sessions", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to load sessions: ${e.message}")
            )
        }
    }
    
    get("/api/characters") {
        try {
            val characters = databaseViewerService.getCharacters()
            call.respond(characters)
        } catch (e: Exception) {
            call.application.environment.log.error("Failed to load characters", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to load characters: ${e.message}")
            )
        }
    }
    
    get("/api/zone-status") {
        try {
            val status = zoneServerService.getStatus()
            call.respond(status)
        } catch (e: Exception) {
            call.respond(mapOf("error" to "Failed to load zone status: ${e.message}"))
        }
    }
    
    // Diagnostic endpoint
    get("/api/diagnostic") {
        val config = call.application.environment.config
        val diagnostic = try {
            mapOf(
                "accountDatabaseUrl" to config.property("ktor.databases.account.url").getString(),
                "characterDatabaseUrl" to config.property("ktor.databases.character.url").getString(),
                "dataDirectoryExists" to java.io.File("./data").exists(),
                "accountDbFileExists" to java.io.File("./data/accountdb.mv.db").exists(),
                "characterDbFileExists" to java.io.File("./data/characterdb.mv.db").exists(),
                "note" to "If dataDirectoryExists is false, account-server and character-server are using in-memory databases and need to be restarted"
            )
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
        call.respond(diagnostic)
    }
    
    // HTML UI
    get("/") {
        call.respondHtml {
            head {
                title { +"Database Viewer - Ragnarok Online" }
                style {
                    unsafe { raw("""
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        margin: 0;
                        padding: 20px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                    }
                    .container {
                        max-width: 1400px;
                        margin: 0 auto;
                        background: white;
                        border-radius: 10px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.3);
                        padding: 30px;
                    }
                    h1 {
                        color: #333;
                        border-bottom: 3px solid #667eea;
                        padding-bottom: 10px;
                    }
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 20px;
                        margin: 20px 0;
                    }
                    .stat-card {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 20px;
                        border-radius: 8px;
                        text-align: center;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                    }
                    .stat-card h3 {
                        margin: 0 0 10px 0;
                        font-size: 14px;
                        opacity: 0.9;
                    }
                    .stat-card .value {
                        font-size: 32px;
                        font-weight: bold;
                        margin: 0;
                    }
                    .section {
                        margin: 30px 0;
                    }
                    .section h2 {
                        color: #667eea;
                        margin-bottom: 15px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 10px;
                        background: white;
                    }
                    th {
                        background: #667eea;
                        color: white;
                        padding: 12px;
                        text-align: left;
                        font-weight: 600;
                    }
                    td {
                        padding: 10px 12px;
                        border-bottom: 1px solid #eee;
                    }
                    tr:hover {
                        background: #f5f5f5;
                    }
                    .refresh-btn {
                        background: #667eea;
                        color: white;
                        border: none;
                        padding: 10px 20px;
                        border-radius: 5px;
                        cursor: pointer;
                        font-size: 14px;
                        margin-bottom: 20px;
                    }
                    .refresh-btn:hover {
                        background: #5568d3;
                    }
                    .status-online {
                        color: #28a745;
                        font-weight: bold;
                    }
                    .status-offline {
                        color: #dc3545;
                        font-weight: bold;
                    }
                    .loading {
                        text-align: center;
                        padding: 20px;
                        color: #666;
                    }
                    """) }
                }
            }
            body {
                div(classes = "container") {
                    h1 { +"🗄️ Database Viewer - Ragnarok Online" }
                    
                    div(classes = "stats-grid") {
                        div(classes = "stat-card") {
                            h3 { +"Total Accounts" }
                            p(classes = "value") { id = "stat-accounts"; +"0" }
                        }
                        div(classes = "stat-card") {
                            h3 { +"Active Sessions" }
                            p(classes = "value") { id = "stat-active-sessions"; +"0" }
                        }
                        div(classes = "stat-card") {
                            h3 { +"Total Sessions" }
                            p(classes = "value") { id = "stat-sessions"; +"0" }
                        }
                        div(classes = "stat-card") {
                            h3 { +"Characters" }
                            p(classes = "value") { id = "stat-characters"; +"0" }
                        }
                    }
                    
                    div(classes = "section") {
                        h2 { +"📊 Statistics" }
                        button(classes = "refresh-btn", type = ButtonType.button) {
                            id = "refresh-stats"
                            +"🔄 Refresh Statistics"
                        }
                    }
                    
                    div(classes = "section") {
                        h2 { +"👤 Accounts" }
                        button(classes = "refresh-btn", type = ButtonType.button) {
                            id = "refresh-accounts"
                            +"🔄 Refresh Accounts"
                        }
                        div { id = "accounts-table"; classes = setOf("loading"); +"Loading..." }
                    }
                    
                    div(classes = "section") {
                        h2 { +"🔐 Sessions" }
                        button(classes = "refresh-btn", type = ButtonType.button) {
                            id = "refresh-sessions"
                            +"🔄 Refresh Sessions"
                        }
                        div { id = "sessions-table"; classes = setOf("loading"); +"Loading..." }
                    }
                    
                    div(classes = "section") {
                        h2 { +"⚔️ Characters" }
                        button(classes = "refresh-btn", type = ButtonType.button) {
                            id = "refresh-characters"
                            +"🔄 Refresh Characters"
                        }
                        div { id = "characters-table"; classes = setOf("loading"); +"Loading..." }
                    }
                    
                    div(classes = "section") {
                        h2 { +"🌍 Zone Server Status" }
                        button(classes = "refresh-btn", type = ButtonType.button) {
                            id = "refresh-zone"
                            +"🔄 Refresh Zone Status"
                        }
                        div { id = "zone-status"; classes = setOf("loading"); +"Loading..." }
                    }
                }
                
                script {
                    unsafe { raw("""
                    async function loadStats() {
                        try {
                            const response = await fetch('/api/stats');
                            if (!response.ok) {
                                throw new Error('HTTP ' + response.status);
                            }
                            const text = await response.text();
                            if (!text || text.trim() === '') {
                                throw new Error('Empty response from server');
                            }
                            const stats = JSON.parse(text);
                            if (stats.error) {
                                throw new Error(stats.error);
                            }
                            document.getElementById('stat-accounts').textContent = stats.accounts || 0;
                            document.getElementById('stat-sessions').textContent = stats.sessions || 0;
                            document.getElementById('stat-active-sessions').textContent = stats.activeSessions || 0;
                            document.getElementById('stat-characters').textContent = stats.characters || 0;
                        } catch (e) {
                            console.error('Failed to load stats:', e);
                            document.getElementById('stat-accounts').textContent = '?';
                            document.getElementById('stat-sessions').textContent = '?';
                            document.getElementById('stat-active-sessions').textContent = '?';
                            document.getElementById('stat-characters').textContent = '?';
                        }
                    }
                    
                    async function loadAccounts() {
                        const table = document.getElementById('accounts-table');
                        try {
                            const response = await fetch('/api/accounts');
                            if (!response.ok) {
                                throw new Error('HTTP ' + response.status);
                            }
                            const text = await response.text();
                            if (!text || text.trim() === '') {
                                throw new Error('Empty response from server');
                            }
                            const accounts = JSON.parse(text);
                            if (accounts.error) {
                                throw new Error(accounts.error);
                            }
                            if (!Array.isArray(accounts)) {
                                throw new Error('Invalid response format');
                            }
                            if (accounts.length === 0) {
                                table.innerHTML = '<p>No accounts found.</p>';
                                return;
                            }
                            table.innerHTML = '<table><thead><tr><th>ID</th><th>Email</th><th>Created At</th><th>Last Login</th></tr></thead><tbody>' +
                                accounts.map(a => '<tr><td>' + a.id.substring(0, 8) + '...</td><td>' + a.email + '</td><td>' + 
                                new Date(a.createdAt).toLocaleString() + '</td><td>' + 
                                (a.lastLogin ? new Date(a.lastLogin).toLocaleString() : 'Never') + '</td></tr>').join('') +
                                '</tbody></table>';
                        } catch (e) {
                            console.error('Failed to load accounts:', e);
                            table.innerHTML = '<p style="color: red;">Error loading accounts: ' + e.message + '</p>';
                        }
                    }
                    
                    async function loadSessions() {
                        const table = document.getElementById('sessions-table');
                        try {
                            const response = await fetch('/api/sessions');
                            if (!response.ok) {
                                throw new Error('HTTP ' + response.status);
                            }
                            const text = await response.text();
                            if (!text || text.trim() === '') {
                                throw new Error('Empty response from server');
                            }
                            const sessions = JSON.parse(text);
                            if (sessions.error) {
                                throw new Error(sessions.error);
                            }
                            if (!Array.isArray(sessions)) {
                                throw new Error('Invalid response format');
                            }
                            if (sessions.length === 0) {
                                table.innerHTML = '<p>No sessions found.</p>';
                                return;
                            }
                            table.innerHTML = '<table><thead><tr><th>ID</th><th>Account ID</th><th>Token</th><th>Created At</th><th>Expires At</th><th>Active</th></tr></thead><tbody>' +
                                sessions.map(s => '<tr><td>' + s.id.substring(0, 8) + '...</td><td>' + s.accountId.substring(0, 8) + '...</td><td>' + 
                                s.token.substring(0, 20) + '...</td><td>' + new Date(s.createdAt).toLocaleString() + '</td><td>' + 
                                new Date(s.expiresAt).toLocaleString() + '</td><td>' + (s.isActive ? '✓' : '✗') + '</td></tr>').join('') +
                                '</tbody></table>';
                        } catch (e) {
                            console.error('Failed to load sessions:', e);
                            table.innerHTML = '<p style="color: red;">Error loading sessions: ' + e.message + '</p>';
                        }
                    }
                    
                    async function loadCharacters() {
                        const table = document.getElementById('characters-table');
                        try {
                            const response = await fetch('/api/characters');
                            if (!response.ok) {
                                throw new Error('HTTP ' + response.status);
                            }
                            const text = await response.text();
                            if (!text || text.trim() === '') {
                                throw new Error('Empty response from server');
                            }
                            const characters = JSON.parse(text);
                            if (characters.error) {
                                throw new Error(characters.error);
                            }
                            if (!Array.isArray(characters)) {
                                throw new Error('Invalid response format');
                            }
                            if (characters.length === 0) {
                                table.innerHTML = '<p>No characters found.</p>';
                                return;
                            }
                            table.innerHTML = '<table><thead><tr><th>ID</th><th>Account ID</th><th>Name</th><th>Created At</th></tr></thead><tbody>' +
                                characters.map(c => '<tr><td>' + c.id.substring(0, 8) + '...</td><td>' + c.accountId.substring(0, 8) + '...</td><td>' + 
                                c.name + '</td><td>' + new Date(c.createdAt).toLocaleString() + '</td></tr>').join('') +
                                '</tbody></table>';
                        } catch (e) {
                            console.error('Failed to load characters:', e);
                            table.innerHTML = '<p style="color: red;">Error loading characters: ' + e.message + '</p>';
                        }
                    }
                    
                    async function loadZoneStatus() {
                        try {
                            const response = await fetch('/api/zone-status');
                            const status = await response.json();
                            const div = document.getElementById('zone-status');
                            div.innerHTML = '<p><strong>Status:</strong> <span class="' + 
                                (status.status === 'online' ? 'status-online' : 'status-offline') + '">' + 
                                status.status.toUpperCase() + '</span></p><p><strong>Message:</strong> ' + status.message + '</p>';
                        } catch (e) {
                            document.getElementById('zone-status').innerHTML = '<p style="color: red;">Error loading zone status: ' + e.message + '</p>';
                        }
                    }
                    
                    // Load all data on page load
                    loadStats();
                    loadAccounts();
                    loadSessions();
                    loadCharacters();
                    loadZoneStatus();
                    
                    // Add refresh button handlers
                    document.getElementById('refresh-stats').addEventListener('click', loadStats);
                    document.getElementById('refresh-accounts').addEventListener('click', loadAccounts);
                    document.getElementById('refresh-sessions').addEventListener('click', loadSessions);
                    document.getElementById('refresh-characters').addEventListener('click', loadCharacters);
                    document.getElementById('refresh-zone').addEventListener('click', loadZoneStatus);
                    
                    // Auto-refresh all data every 5 seconds
                    function refreshAll() {
                        loadStats();
                        loadAccounts();
                        loadSessions();
                        loadCharacters();
                        loadZoneStatus();
                    }
                    
                    setInterval(refreshAll, 5000);
                    """) }
                }
            }
        }
    }
}

