(function() {
    const iceServers = [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' },
        { urls: 'stun:stun3.l.google.com:19302' },
        { urls: 'stun:stun4.l.google.com:19302' }
    ];

    let peer = null;
    let conn = null;
    let localId = null;
    let qrCode = null;

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
        debugLog: document.getElementById('debug-log'),
        progressArea: document.getElementById('transfer-progress'),
        progressFill: document.getElementById('progress-fill'),
        progressText: document.getElementById('progress-text'),
        qrContainer: document.getElementById('qrcode-container'),
        checkUpdateBtn: document.getElementById('check-update-btn'),
        shareAppBtn: document.getElementById('share-app-btn'),
        appVersionSpan: document.getElementById('app-version')
    };

    const GITHUB_REPO_URL = 'https://github.com/jnetai-clawbot/p2p-chat/releases/latest';

    function log(msg, isError = false) {
        const time = new Date().toLocaleTimeString();
        const entry = document.createElement('div');
        entry.textContent = `[${time}] ${msg}`;
        if (isError) entry.style.color = '#f44336';
        elements.debugLog.prepend(entry);
        if (window.AndroidBridge) {
            window.AndroidBridge.log(msg);
        }
    }

    function generateRandomId() {
        // Generate a 4-8 digit numeric Pin/ID
        return Math.floor(1000 + Math.random() * 999999).toString();
    }

    function initPeer(requestedId = null) {
        if (peer) {
            peer.destroy();
        }

        const idToUse = requestedId || generateRandomId();
        log(`Initializing Peer with ID: ${idToUse}`);
        
        peer = new Peer(idToUse, {
            config: { iceServers: iceServers },
            debug: 1
        });

        peer.on('open', (id) => {
            localId = id;
            elements.localId.textContent = id;
            updateStatus('Disconnected', 'status-disconnected');
            log(`Peer opened with ID: ${id}`);
            generateQrCode(id);
        });

        peer.on('connection', (connection) => {
            if (conn) {
                log('Multiple connections not supported, closing new one');
                connection.close();
                return;
            }
            log(`Incoming connection from: ${connection.peer}`);
            setupConnection(connection);
        });

        peer.on('error', (err) => {
            log(`Peer error: ${err.type} - ${err.message}`, true);
            if (err.type === 'unavailable-id') {
                log('ID already taken, retrying with new ID...');
                initPeer();
            } else if (window.AndroidBridge) {
                window.AndroidBridge.onError('P001', err.message);
            }
        });

        peer.on('disconnected', () => {
            log('Peer disconnected from server');
            updateStatus('Offline (Disconnected)', 'status-disconnected');
        });
    }

    function generateQrCode(text) {
        elements.qrContainer.innerHTML = '';
        qrCode = new QRCode(elements.qrContainer, {
            text: text,
            width: 150,
            height: 150,
            colorDark: "#000000",
            colorLight: "#ffffff",
            correctLevel: QRCode.CorrectLevel.H
        });
    }

    function setupConnection(connection) {
        conn = connection;
        
        conn.on('open', () => {
            log(`Data channel open with: ${conn.peer}`);
            updateStatus(`Connected to ${conn.peer}`, 'status-connected');
            showChat();
        });

        conn.on('data', (data) => {
            handleReceivedData(data);
        });

        conn.on('close', () => {
            log('Connection closed');
            updateStatus('Disconnected', 'status-disconnected');
            hideChat();
            conn = null;
        });

        conn.on('error', (err) => {
            log(`Connection error: ${err.message}`, true);
            if (window.AndroidBridge) {
                window.AndroidBridge.onError('C001', err.message);
            }
        });
    }

    function handleReceivedData(data) {
        if (typeof data === 'string') {
            try {
                const msg = JSON.parse(data);
                if (msg.type === 'chat') {
                    addMessage(msg.text, 'received');
                } else if (msg.type === 'nudge') {
                    handleNudge();
                }
            } catch (e) {
                addMessage(data, 'received');
            }
        } else if (typeof data === 'object' && data.type === 'file') {
            log(`Received file: ${data.name} (${data.size} bytes)`);
            if (window.AndroidBridge) {
                window.AndroidBridge.saveReceivedFile(data.name, data.data);
            }
            addMessage(`Received file: ${data.name}`, 'system');
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

    function sendMessage() {
        const text = elements.messageInput.value.trim();
        if (text && conn && conn.open) {
            const msg = { type: 'chat', text: text };
            conn.send(JSON.stringify(msg));
            addMessage(text, 'sent');
            elements.messageInput.value = '';
        }
    }

    function handleNudge() {
        document.body.classList.add('shake');
        if (window.AndroidBridge) {
            window.AndroidBridge.vibrate(500);
        }
        addMessage('Nudge received!', 'system');
        setTimeout(() => document.body.classList.remove('shake'), 500);
    }

    // Bridge hooks
    window.onQrScanResult = function(result) {
        log(`QR scan result: ${result}`);
        elements.remoteIdInput.value = result;
        connectToPeer(result);
    };

    window.onQrScanError = function(error) {
        log(`QR scan error: ${error}`, true);
    };

    window.onFilePicked = function(fileObj) {
        if (!fileObj) return;
        log(`File picked: ${fileObj.name} (${fileObj.size} bytes)`);
        if (conn && conn.open) {
            const data = {
                type: 'file',
                name: fileObj.name,
                size: fileObj.size,
                mimeType: fileObj.mimeType,
                data: fileObj.data
            };
            conn.send(data);
            addMessage(`Sent file: ${fileObj.name}`, 'system');
        }
    };

    window.onFilePickedError = function(error) {
        log(`File pick error: ${error}`, true);
    };

    window.onFileSaved = function(path, size) {
        addMessage(`File saved to: ${path}`, 'system');
    };

    window.onFileSavedError = function(error) {
        log(`File save error: ${error}`, true);
    };

    function connectToPeer(id) {
        if (!id) return;
        log(`Connecting to: ${id}...`);
        updateStatus(`Connecting to ${id}...`, 'status-connecting');
        const connection = peer.connect(id, {
            reliable: true
        });
        setupConnection(connection);
    }

    // Event Listeners
    elements.connectBtn.addEventListener('click', () => {
        const id = elements.remoteIdInput.value.trim();
        connectToPeer(id);
    });

    elements.sendBtn.addEventListener('click', sendMessage);
    elements.messageInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });

    elements.scanQrBtn.addEventListener('click', () => {
        if (window.AndroidBridge) {
            window.AndroidBridge.scanQrCode();
        } else {
            log('QR Scanner not available in browser', true);
        }
    });

    elements.newIdBtn.addEventListener('click', () => {
        log('Generating new Peer ID...');
        initPeer();
    });

    elements.copyIdBtn.addEventListener('click', () => {
        if (localId) {
            if (window.AndroidBridge) {
                window.AndroidBridge.copyToClipboard(localId);
            } else {
                navigator.clipboard.writeText(localId);
            }
            elements.copyIdBtn.textContent = '✅';
            setTimeout(() => elements.copyIdBtn.textContent = '📋', 2000);
        }
    });

    elements.pickFileBtn.addEventListener('click', () => {
        if (window.AndroidBridge) {
            window.AndroidBridge.pickFile();
        }
    });

    elements.nudgeBtn.addEventListener('click', () => {
        if (conn && conn.open) {
            conn.send(JSON.stringify({ type: 'nudge' }));
            addMessage('Nudge sent!', 'system');
        }
    });

    elements.checkUpdateBtn.addEventListener('click', () => {
        window.open(GITHUB_REPO_URL, '_blank');
    });

    elements.shareAppBtn.addEventListener('click', () => {
        if (window.AndroidBridge) {
            window.AndroidBridge.shareApp(GITHUB_REPO_URL);
        } else {
            log('Share feature only available in app', true);
        }
    });

    // Start
    initPeer();
})();
