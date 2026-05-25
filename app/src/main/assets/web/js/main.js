(function() {
    const STUN_SERVERS = [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' },
        { urls: 'stun:stun3.l.google.com:19302' },
        { urls: 'stun:stun4.l.google.com:19302' }
    ];

    const TURN_SERVERS = [
        { urls: 'turn:openrelay.metered.ca:80', username: 'openrelayproject', credential: 'openrelayproject' },
        { urls: 'turn:openrelay.metered.ca:443', username: 'openrelayproject', credential: 'openrelayproject' },
        { urls: 'turn:openrelay.metered.ca:443?transport=tcp', username: 'openrelayproject', credential: 'openrelayproject' }
    ];

    let peer = null;
    let conn = null;
    let localId = null;
    let qrCode = null;
    let remotePeerId = null;
    let isUserEndingChat = false;

    const elements = {
        localId: document.getElementById('local-id'),
        remoteIdInput: document.getElementById('remote-id-input'),
        connectBtn: document.getElementById('connect-btn'),
        scanQrBtn: document.getElementById('scan-qr-btn'),
        newIdBtn: document.getElementById('new-id-btn'),
        copyIdBtn: document.getElementById('copy-id-btn'),
        status: document.getElementById('connection-status'),
        chatSection: document.getElementById('chat-section'),
        idSection: document.getElementById('id-section'),
        connectSection: document.getElementById('connect-section'),
        chatMessages: document.getElementById('chat-messages'),
        messageInput: document.getElementById('message-input'),
        sendBtn: document.getElementById('send-btn'),
        pickFileBtn: document.getElementById('pick-file-btn'),
        nudgeBtn: document.getElementById('nudge-btn'),
        endChatBtn: document.getElementById('end-chat-btn'),
        debugLog: document.getElementById('debug-log'),
        qrContainer: document.getElementById('qrcode-container'),
        openSettingsBtn: document.getElementById('open-settings-btn'),
        closeSettingsBtn: document.getElementById('close-settings-btn'),
        settingsModal: document.getElementById('settings-modal'),
        checkUpdateBtn: document.getElementById('check-update-btn'),
        shareAppBtn: document.getElementById('share-app-btn'),
        publicIp: document.getElementById('public-ip'),
        webrtcSupport: document.getElementById('webrtc-support')
    };

    const settings = {
        autoReconnect: document.getElementById('setting-auto-reconnect'),
        screenOn: document.getElementById('setting-screen-on'),
        allowFiles: document.getElementById('setting-allow-files'),
        saveFolder: document.getElementById('setting-save-folder'),
        vibrate: document.getElementById('setting-vibrate'),
        useTurn: document.getElementById('setting-use-turn'),
        debug: document.getElementById('setting-debug')
    };

    const GITHUB_REPO_URL = 'https://github.com/jnetai-clawbot/p2p-chat/releases/latest';

    const SETTINGS_KEY = 'p2pchat_settings';

    function loadSettings() {
        try {
            const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY));
            if (saved) {
                if (saved.autoReconnect !== undefined) settings.autoReconnect.checked = saved.autoReconnect;
                if (saved.screenOn !== undefined) settings.screenOn.checked = saved.screenOn;
                if (saved.allowFiles !== undefined) settings.allowFiles.checked = saved.allowFiles;
                if (saved.vibrate !== undefined) settings.vibrate.checked = saved.vibrate;
                if (saved.useTurn !== undefined) settings.useTurn.checked = saved.useTurn;
                if (saved.debug !== undefined) settings.debug.checked = saved.debug;
                if (saved.saveFolder !== undefined) settings.saveFolder.value = saved.saveFolder;
            }
        } catch (e) { /* ignore */ }
    }

    function saveSettings() {
        try {
            localStorage.setItem(SETTINGS_KEY, JSON.stringify({
                autoReconnect: settings.autoReconnect.checked,
                screenOn: settings.screenOn.checked,
                allowFiles: settings.allowFiles.checked,
                vibrate: settings.vibrate.checked,
                useTurn: settings.useTurn.checked,
                debug: settings.debug.checked,
                saveFolder: settings.saveFolder.value
            }));
        } catch (e) { /* ignore */ }
    }

    function log(msg, isError = false) {
        if (!settings.debug.checked && !isError) return;
        const time = new Date().toLocaleTimeString();
        const entry = document.createElement('div');
        entry.textContent = `[${time}] ${msg}`;
        if (isError) entry.style.color = '#f44336';
        elements.debugLog.prepend(entry);
        if (window.AndroidBridge) window.AndroidBridge.log(msg);
    }

    function generateRandomId() {
        return Math.floor(1000 + Math.random() * 999999).toString();
    }

    function detectNetwork() {
        elements.webrtcSupport.textContent = (window.RTCPeerConnection) ? "✅ Supported" : "❌ Not Supported";
        fetch('https://api.ipify.org?format=json')
            .then(res => res.json())
            .then(data => { elements.publicIp.textContent = data.ip; })
            .catch(() => { elements.publicIp.textContent = "Unable to detect"; });
    }

    function applyScreenSetting() {
        if (window.AndroidBridge) {
            window.AndroidBridge.setKeepScreenOn(settings.screenOn.checked);
        }
    }

    function initPeer() {
        if (peer) peer.destroy();
        const idToUse = generateRandomId();
        const iceServers = [...STUN_SERVERS];
        if (settings.useTurn.checked) iceServers.push(...TURN_SERVERS);
        
        peer = new Peer(idToUse, { 
            config: { iceServers: iceServers, iceTransportPolicy: 'all' }, 
            debug: 1 
        });

        peer.on('open', (id) => {
            localId = id;
            elements.localId.textContent = id;
            updateStatus('Disconnected', 'status-disconnected');
            generateQrCode(id);
        });

        peer.on('connection', (connection) => {
            if (conn) { connection.close(); return; }
            setupConnection(connection);
        });

        peer.on('error', (err) => {
            log(`Peer error: ${err.type}`, true);
            if (err.type === 'unavailable-id') initPeer();
        });
    }

    function generateQrCode(text) {
        elements.qrContainer.innerHTML = '';
        qrCode = new QRCode(elements.qrContainer, {
            text: text, width: 150, height: 150,
            colorDark: "#000000", colorLight: "#ffffff",
            correctLevel: QRCode.CorrectLevel.H
        });
    }

    function setupConnection(connection) {
        conn = connection;
        remotePeerId = conn.peer;
        isUserEndingChat = false;
        
        conn.on('open', () => {
            updateStatus(`Connected to ${conn.peer}`, 'status-connected');
            showChat();
            addMessage(`System: Connected to ${conn.peer}`, 'system');
        });

        conn.on('data', handleReceivedData);

        conn.on('close', () => {
            updateStatus('Disconnected', 'status-disconnected');
            addMessage('System: Connection closed', 'system');
            if (!isUserEndingChat && settings.autoReconnect.checked && remotePeerId) {
                setTimeout(() => connectToPeer(remotePeerId), 3000);
            } else {
                hideChat();
                conn = null;
                remotePeerId = null;
            }
        });
    }

    function handleReceivedData(data) {
        if (typeof data === 'string') {
            try {
                const msg = JSON.parse(data);
                if (msg.type === 'chat') addMessage(msg.text, 'received');
                else if (msg.type === 'nudge') handleNudge();
            } catch (e) { addMessage(data, 'received'); }
        } else if (typeof data === 'object' && data.type === 'file') {
            if (!settings.allowFiles.checked) {
                log(`Rejected file: ${data.name} (File transfers disabled)`);
                return;
            }
            const folder = settings.saveFolder.value.trim();
            if (window.AndroidBridge) {
                window.AndroidBridge.saveReceivedFile(data.name, data.data, folder);
            }
            addMessage(`Received: ${data.name}` + (folder ? ` → Downloads/${folder}` : ''), 'system');
        }
    }

    function addMessage(text, type) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `message message-${type}`;
        msgDiv.textContent = text;
        elements.chatMessages.appendChild(msgDiv);
        elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
    }

    function updateStatus(text, className) {
        elements.status.textContent = text;
        elements.status.className = className;
    }

    function showChat() {
        elements.chatSection.classList.remove('hidden');
        elements.idSection.classList.add('hidden');
        elements.connectSection.classList.add('hidden');
    }

    function hideChat() {
        elements.chatSection.classList.add('hidden');
        elements.idSection.classList.remove('hidden');
        elements.connectSection.classList.remove('hidden');
    }

    function connectToPeer(id) {
        if (!id) return;
        updateStatus(`Connecting...`, 'status-connecting');
        setupConnection(peer.connect(id, { reliable: true }));
    }

    function handleNudge() {
        document.body.classList.add('shake');
        if (settings.vibrate.checked && window.AndroidBridge) window.AndroidBridge.vibrate(500);
        addMessage('Nudge received!', 'system');
        setTimeout(() => document.body.classList.remove('shake'), 500);
    }

    // Bridge hooks
    window.onQrScanResult = (res) => { elements.remoteIdInput.value = res; connectToPeer(res); };
    window.onFilePicked = (file) => {
        if (file && conn && conn.open) {
            conn.send({ type: 'file', name: file.name, size: file.size, data: file.data });
            addMessage(`Sent: ${file.name}`, 'system');
        }
    };
    window.onFileSaved = (path) => {
        const folder = settings.saveFolder.value.trim();
        if (folder) {
            addMessage(`Saved to Downloads/${folder}`, 'system');
        } else {
            addMessage('Saved to Downloads', 'system');
        }
    };

    // UI Events
    elements.connectBtn.addEventListener('click', () => connectToPeer(elements.remoteIdInput.value.trim()));
    elements.sendBtn.addEventListener('click', () => {
        const text = elements.messageInput.value.trim();
        if (text && conn && conn.open) {
            conn.send(JSON.stringify({ type: 'chat', text }));
            addMessage(text, 'sent');
            elements.messageInput.value = '';
        }
    });
    elements.endChatBtn.addEventListener('click', () => {
        isUserEndingChat = true;
        if (conn) conn.close();
    });

    // Save settings on any setting change
    function onSettingChange() { saveSettings(); }
    settings.autoReconnect.addEventListener('change', onSettingChange);
    settings.screenOn.addEventListener('change', () => { applyScreenSetting(); saveSettings(); });
    settings.allowFiles.addEventListener('change', onSettingChange);
    settings.vibrate.addEventListener('change', onSettingChange);
    settings.debug.addEventListener('change', onSettingChange);
    settings.saveFolder.addEventListener('change', onSettingChange);

    elements.openSettingsBtn.addEventListener('click', () => elements.settingsModal.classList.remove('hidden'));
    elements.closeSettingsBtn.addEventListener('click', () => elements.settingsModal.classList.add('hidden'));
    elements.newIdBtn.addEventListener('click', initPeer);
    elements.scanQrBtn.addEventListener('click', () => window.AndroidBridge && window.AndroidBridge.scanQrCode());
    elements.copyIdBtn.addEventListener('click', () => {
        if (localId) {
            if (window.AndroidBridge) window.AndroidBridge.copyToClipboard(localId);
            elements.copyIdBtn.textContent = '✅';
            setTimeout(() => elements.copyIdBtn.textContent = '📋', 2000);
        }
    });
    elements.pickFileBtn.addEventListener('click', () => window.AndroidBridge && window.AndroidBridge.pickFile());
    elements.nudgeBtn.addEventListener('click', () => {
        if (conn && conn.open) {
            conn.send(JSON.stringify({ type: 'nudge' }));
            addMessage('Nudge sent!', 'system');
        }
    });
    elements.checkUpdateBtn.addEventListener('click', () => window.open(GITHUB_REPO_URL, '_blank'));
    elements.shareAppBtn.addEventListener('click', () => window.AndroidBridge && window.AndroidBridge.shareApp(GITHUB_REPO_URL));

    settings.useTurn.addEventListener('change', initPeer);
    settings.screenOn.addEventListener('change', applyScreenSetting);

    loadSettings();
    detectNetwork();
    initPeer();
    applyScreenSetting(); // Default is ON
})();
