/**
 * Causa AI — Landing Page Interactive Logic, 3D WebGL Neural Mesh, Scenario Simulator & Animated Architecture Flow
 */

document.addEventListener('DOMContentLoaded', () => {
    // 1. Initialize Lucide Icons
    if (window.lucide) {
        window.lucide.createIcons();
    }

    // 2. Initialize 3D WebGL Hero Canvas using Three.js
    initThreeDHero();

    // 3. Initialize Interactive Scenario Simulator
    initScenarioSimulator();

    // 4. Initialize Code Snippet Tabs
    initCodeTabs();

    // 5. Initialize Live Animated Architecture Pipeline Flow
    initArchitectureFlowAnimation();
});

// ==========================================
// 1. Three.js 3D Neural Cluster & Node Mesh
// ==========================================
function initThreeDHero() {
    const container = document.getElementById('hero-canvas-container');
    if (!container || typeof THREE === 'undefined') return;

    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x030712, 0.0018);

    const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 1000);
    camera.position.z = 180;
    camera.position.y = 20;

    const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    // Group for entire rotating network
    const networkGroup = new THREE.Group();
    scene.add(networkGroup);

    // Create Nodes (Pods / Services / MCP Agents)
    const nodeCount = 70;
    const geometry = new THREE.SphereGeometry(1.2, 16, 16);
    const nodes = [];
    const positions = [];

    const colors = [0x00f2ff, 0x3b82f6, 0xa855f7, 0x10b981];

    for (let i = 0; i < nodeCount; i++) {
        const material = new THREE.MeshBasicMaterial({
            color: colors[Math.floor(Math.random() * colors.length)],
            transparent: true,
            opacity: 0.85
        });
        const mesh = new THREE.Mesh(geometry, material);
        
        const radius = 60 + Math.random() * 60;
        const theta = Math.random() * Math.PI * 2;
        const phi = Math.acos((Math.random() * 2) - 1);

        mesh.position.x = radius * Math.sin(phi) * Math.cos(theta);
        mesh.position.y = radius * Math.sin(phi) * Math.sin(theta) * 0.6;
        mesh.position.z = radius * Math.cos(phi);

        mesh.userData = {
            velocity: new THREE.Vector3(
                (Math.random() - 0.5) * 0.15,
                (Math.random() - 0.5) * 0.15,
                (Math.random() - 0.5) * 0.15
            ),
            originalPos: mesh.position.clone()
        };

        networkGroup.add(mesh);
        nodes.push(mesh);
        positions.push(mesh.position);
    }

    // Connect Nodes with Lines (Mesh Graph)
    const lineMaterial = new THREE.LineBasicMaterial({
        color: 0x38bdf8,
        transparent: true,
        opacity: 0.18
    });

    const linesGeometry = new THREE.BufferGeometry();
    const linePositions = [];
    const maxDist = 45;

    for (let i = 0; i < nodeCount; i++) {
        for (let j = i + 1; j < nodeCount; j++) {
            const dist = nodes[i].position.distanceTo(nodes[j].position);
            if (dist < maxDist) {
                linePositions.push(
                    nodes[i].position.x, nodes[i].position.y, nodes[i].position.z,
                    nodes[j].position.x, nodes[j].position.y, nodes[j].position.z
                );
            }
        }
    }

    linesGeometry.setAttribute('position', new THREE.Float32BufferAttribute(linePositions, 3));
    const linesMesh = new THREE.LineSegments(linesGeometry, lineMaterial);
    networkGroup.add(linesMesh);

    // Glowing Central Agent Core Sphere
    const coreGeo = new THREE.IcosahedronGeometry(14, 2);
    const coreMat = new THREE.MeshBasicMaterial({
        color: 0x00f2ff,
        wireframe: true,
        transparent: true,
        opacity: 0.35
    });
    const coreMesh = new THREE.Mesh(coreGeo, coreMat);
    networkGroup.add(coreMesh);

    // Mouse Interaction
    let mouseX = 0;
    let mouseY = 0;
    let targetRotationX = 0;
    let targetRotationY = 0;

    window.addEventListener('mousemove', (e) => {
        mouseX = (e.clientX - window.innerWidth / 2) * 0.0005;
        mouseY = (e.clientY - window.innerHeight / 2) * 0.0005;
    });

    // Resize Handler
    window.addEventListener('resize', () => {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    });

    // Animation Loop
    function animate() {
        requestAnimationFrame(animate);

        targetRotationY += 0.0015;
        networkGroup.rotation.y += (targetRotationY + mouseX - networkGroup.rotation.y) * 0.05;
        networkGroup.rotation.x += (mouseY - networkGroup.rotation.x) * 0.05;

        coreMesh.rotation.y -= 0.005;
        coreMesh.rotation.x += 0.003;

        // Dynamic node floating
        nodes.forEach((node) => {
            node.position.add(node.userData.velocity);
            if (node.position.distanceTo(node.userData.originalPos) > 8) {
                node.userData.velocity.negate();
            }
        });

        renderer.render(scene, camera);
    }
    animate();
}

// ==========================================
// 2. Scenario Simulator Data & Controller
// ==========================================
const scenarios = {
    oom: {
        alertName: "KubePodContainerOOMKilled",
        alertStatus: "CRITICAL",
        alertBadgeClass: "bg-rose-500/20 text-rose-300 border-rose-500/30",
        namespace: "payments-prod",
        pod: "checkout-service-7bb8c-x9k2p",
        signals: {
            podStatus: "Terminated (Exit 137)",
            prom: "1024MiB / 1024MiB (100% Cgroup Max)",
            logs: "java.lang.OutOfMemoryError: Java heap space",
            events: "Memory cgroup out of memory: Killed process 14829 (java)",
            jfr: "Old Gen 96% | 4.2s GC Pause before crash",
            kruize: "Upsize limit to 2048MiB (Under-provisioned)"
        },
        category: "OOM_KILLED (Container Cgroup Memory Exhaustion)",
        confidence: "0.98 / 1.0",
        summary: "JVM heap was configured with -Xmx896m inside a container strictly capped at 1024MiB. Heavy JSON batch deserialization caused direct memory allocations and thread stack expansion, exceeding cgroup limits and triggering Linux kernel OOM killer (exit code 137).",
        evidence: [
            { title: "SIGNAL: JFR OLD GEN HEAP", desc: "Tenured pool rose to 96% occupancy with 4,200ms Full GC pause prior to crash." },
            { title: "SIGNAL: K8S CGROUP OOM", desc: "Kernel dmesg logged task killed with total-vm:1.4GB, anon-rss:1023MB." }
        ],
        immediate: "Increase container memory limit to 2048MiB via kubectl patch to prevent immediate CrashLoopBackOff.",
        cmd: "kubectl set resources deployment checkout-service -c app --limits=memory=2048Mi --requests=1536Mi",
        permanent: "Configure JVM -XX:MaxRAMPercentage=75.0 and stream large JSON bodies with Jackson streaming parser.",
        kruizeRef: "Kruize Recommendation ID: KRZ-REC-9482-OPTIMAL (ROI +35% SLA Reliability)"
    },
    heap: {
        alertName: "JVMHighMemoryPressureAlert",
        alertStatus: "WARNING",
        alertBadgeClass: "bg-amber-500/20 text-amber-300 border-amber-500/30",
        namespace: "inventory-prod",
        pod: "stock-sync-worker-5cc94-m32fa",
        signals: {
            podStatus: "Running (High GC Thrashing)",
            prom: "Heap RSS 89% (Rising 1.2% / min)",
            logs: "WARN Slow Query Cache growing unbounded (1.8M keys)",
            events: "Readiness probe slow: HTTP 200 took > 4200ms",
            jfr: "ConcurrentHashMap holding 580MB in Tenured Gen",
            kruize: "Fix memory leak before vertical scaling"
        },
        category: "POSSIBLE_OOM_KILLED (Unbounded Cache Memory Leak)",
        confidence: "0.94 / 1.0",
        summary: "Static ConcurrentHashMap in StockCacheService retained deserialized catalog items indefinitely without TTL or eviction policy. Tenured Generation retention prevented garbage collection from freeing memory.",
        evidence: [
            { title: "SIGNAL: JFR MEMORY OBJECT HISTOGRAM", desc: "StockItemDto instances count 1,842,910 retaining 580MB in Old Gen." },
            { title: "SIGNAL: APPLICATION LOGS", desc: "Cache eviction scheduled tasks failed due to unhandled null reference." }
        ],
        immediate: "Trigger application cache purge endpoint or gracefully restart pod to reset tenured heap.",
        cmd: "kubectl rollout restart deployment/stock-sync-worker -n inventory-prod",
        permanent: "Replace static map with Caffeine cache using maximumSize(50000) and expireAfterWrite(30m).",
        kruizeRef: "Kruize Advisory: Workload memory growth rate anomaly detected"
    },
    gc: {
        alertName: "KubePodExcessiveGCPauseTime",
        alertStatus: "WARNING",
        alertBadgeClass: "bg-cyan-500/20 text-cyan-300 border-cyan-500/30",
        namespace: "order-stream",
        pod: "order-processor-6fd11-j411a",
        signals: {
            podStatus: "Running (CPU Throttled)",
            prom: "CPU Throttle 42% | Heap 72%",
            logs: "G1 Evacuation Pause took 3,840ms (Target 200ms)",
            events: "Liveness probe timeout due to stop-the-world GC",
            jfr: "G1 Humongous Allocations > 45% of young generation",
            kruize: "Increase G1RegionSize to 16M and allocate +1.5 CPU"
        },
        category: "POSSIBLE_GC_PAUSE (G1 Humongous Object Thrashing)",
        confidence: "0.96 / 1.0",
        summary: "Frequent allocations of byte arrays exceeding 50% of the G1 region size triggered continuous concurrent marking cycles and stop-the-world Humongous Evacuation pauses, resulting in thread stalls and CPU throttling.",
        evidence: [
            { title: "SIGNAL: JFR GC_ANALYSIS_CRYOSTAT", desc: "1,200 Humongous allocations observed in 60s recording window." },
            { title: "SIGNAL: PROMETHEUS CPU THROTTLE", desc: "container_cpu_cfs_throttled_periods_total spiked +380%." }
        ],
        immediate: "Adjust JVM flags to set -XX:G1HeapRegionSize=16m and reduce humongous classification frequency.",
        cmd: "kubectl set env deployment/order-processor JAVA_OPTS='-XX:+UseG1GC -XX:G1HeapRegionSize=16m'",
        permanent: "Pool byte buffer allocations using Netty ByteBuf pool to prevent large transient heap byte[] allocations.",
        kruizeRef: "Kruize Tunable: Align G1RegionSize with 95th percentile payload size"
    },
    threads: {
        alertName: "JVMThreadPoolExhaustion",
        alertStatus: "CRITICAL",
        alertBadgeClass: "bg-purple-500/20 text-purple-300 border-purple-500/30",
        namespace: "auth-gateway",
        pod: "auth-service-89f4b-qq12w",
        signals: {
            podStatus: "Running (Requests Queued)",
            prom: "Active Threads: 200/200 (100% Saturation)",
            logs: "RejectedExecutionException: Thread pool executor full",
            events: "Gateway 504 Gateway Timeout on /oauth/token",
            jfr: "184 threads blocked on DB connection pool lock",
            kruize: "Upsize Agroal connection pool & configure virtual threads"
        },
        category: "THREAD_STARVATION (Downstream Connection Pool Bottleneck)",
        confidence: "0.97 / 1.0",
        summary: "All 200 HTTP worker threads became blocked waiting for available PostgreSQL database connections from an undersized connection pool (max-size=20), leading to cascade thread starvation.",
        evidence: [
            { title: "SIGNAL: JFR THREAD_ANALYSIS", desc: "184 threads parked in WAITING state at AgroalDataSource.getConnection()." },
            { title: "SIGNAL: PROMETHEUS AGROAL METRICS", desc: "agroal_active_connections reached max 20 with 450+ awaiting acquisition." }
        ],
        immediate: "Increase connection pool max-size from 20 to 80 via Quarkus dynamic configuration.",
        cmd: "kubectl set env deployment/auth-service QUARKUS_DATASOURCE_JDBC_MAX_SIZE=80",
        permanent: "Enable Java 21 Virtual Threads (Loom) in Quarkus and add circuit breaker on slow DB queries.",
        kruizeRef: "Kruize Recommendation ID: KRZ-PERF-3301-IO"
    }
};

function initScenarioSimulator() {
    const tabs = document.querySelectorAll('.scenario-tab');
    const triggerBtn = document.getElementById('trigger-rca-btn');

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active-tab'));
            tab.classList.add('active-tab');

            const scenarioKey = tab.getAttribute('data-scenario');
            loadScenario(scenarioKey);
        });
    });

    if (triggerBtn) {
        triggerBtn.addEventListener('click', () => {
            triggerBtn.classList.add('scale-95');
            setTimeout(() => triggerBtn.classList.remove('scale-95'), 150);

            const activeTab = document.querySelector('.scenario-tab.active-tab');
            const scenarioKey = activeTab ? activeTab.getAttribute('data-scenario') : 'oom';
            loadScenario(scenarioKey, true);
        });
    }
}

function loadScenario(key, animate = false) {
    const data = scenarios[key];
    if (!data) return;

    // Update Inbound Alert
    document.getElementById('alert-name').textContent = data.alertName;
    document.getElementById('alert-namespace').textContent = data.namespace;
    document.getElementById('alert-pod').textContent = data.pod;

    const badge = document.getElementById('alert-status-badge');
    badge.textContent = data.alertStatus;
    badge.className = `px-2 py-0.5 rounded text-[10px] font-mono border ${data.alertBadgeClass}`;

    // Update Telemetry Signals
    document.getElementById('sig-pod-status').textContent = data.signals.podStatus;
    document.getElementById('sig-prom').textContent = data.signals.prom;
    document.getElementById('sig-logs').textContent = data.signals.logs;
    document.getElementById('sig-events').textContent = data.signals.events;
    document.getElementById('sig-jfr').textContent = data.signals.jfr;
    document.getElementById('sig-kruize').textContent = data.signals.kruize;

    // Update RCA Result Box
    document.getElementById('rca-category').textContent = data.category;
    document.getElementById('rca-confidence').textContent = data.confidence;
    document.getElementById('rca-summary').textContent = data.summary;

    // Update Evidence Matrix
    const matrix = document.getElementById('evidence-matrix');
    matrix.innerHTML = data.evidence.map(e => `
        <div class="p-3 rounded-lg bg-slate-900/50 border border-white/5 text-xs">
            <div class="text-slate-400 font-mono text-[10px] mb-1">${e.title}</div>
            <div class="text-slate-200 font-mono text-xs">${e.desc}</div>
        </div>
    `).join('');

    // Update Remediation Actions
    document.getElementById('rec-immediate').textContent = data.immediate;
    document.getElementById('rec-cmd').textContent = data.cmd;
    document.getElementById('rec-permanent').textContent = data.permanent;
    document.getElementById('rec-kruize-ref').textContent = data.kruizeRef;

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

// ==========================================
// 3. Code Snippet Tabs Controller
// ==========================================
const codeSnippets = {
    demos: `<span class="text-slate-500"># 1. Clone Causa Demos repository</span>
<span class="text-cyan-400">git clone</span> https://github.com/causaai/causa-demos.git
<span class="text-cyan-400">cd</span> causa-demos/kind

<span class="text-slate-500"># 2. Run the demo setup script (spins up a local Kind cluster)</span>
<span class="text-emerald-400">./demo.sh</span>`,

    dev: `<span class="text-slate-500"># 1. Clone Causa repository</span>
<span class="text-cyan-400">git clone</span> https://github.com/causaai/causa.git
<span class="text-cyan-400">cd</span> causa-backend

<span class="text-slate-500"># 2. Run Quarkus in Live-Reload Dev Mode (PostgreSQL + pgvector autowired)</span>
<span class="text-emerald-400">./mvnw compile quarkus:dev</span>

<span class="text-slate-500"># Quarkus Dev UI: http://localhost:8080/q/dev/
# Health Check: http://localhost:8080/api/v1/healthz</span>`,

    webhook: `<span class="text-slate-500"># Prometheus Alertmanager config (alertmanager.yml)</span>
<span class="text-purple-400">receivers:</span>
  - <span class="text-purple-300">name:</span> <span class="text-cyan-300">'causa-agent-webhook'</span>
    <span class="text-purple-300">webhook_configs:</span>
      - <span class="text-purple-300">url:</span> <span class="text-emerald-300">'http://causa-service.causa-system:8080/api/v1/webhooks/alerts'</span>
        <span class="text-purple-300">send_resolved:</span> <span class="text-amber-300">true</span>
        <span class="text-purple-300">http_config:</span>
          <span class="text-purple-300">bearer_token:</span> <span class="text-emerald-300">'$CAUSA_WEBHOOK_SECRET'</span>`,

    curl: `<span class="text-slate-500"># Trigger autonomous RCA manually for a pod anomaly</span>
<span class="text-cyan-400">curl</span> -X POST http://localhost:8080/api/v1/webhooks/alerts \\
  -H <span class="text-emerald-300">"Content-Type: application/json"</span> \\
  -d <span class="text-amber-300">'{
    "receiver": "causa-webhook",
    "status": "firing",
    "alerts": [{
      "status": "firing",
      "labels": {
        "alertname": "KubePodContainerOOMKilled",
        "namespace": "payments-prod",
        "pod": "checkout-service-7bb8c-x9k2p",
        "severity": "critical"
      }
    }]
  }'</span>`
};

function initCodeTabs() {
    const tabs = document.querySelectorAll('.code-tab');
    const display = document.getElementById('code-display');
    const copyBtn = document.getElementById('copy-code-btn');

    let currentTab = 'demos';

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active-code-tab', 'text-white', 'bg-slate-800'));
            tabs.forEach(t => t.classList.add('text-slate-400'));

            tab.classList.add('active-code-tab', 'text-white', 'bg-slate-800');
            tab.classList.remove('text-slate-400');

            currentTab = tab.getAttribute('data-tab');
            display.innerHTML = codeSnippets[currentTab];
        });
    });

    if (copyBtn) {
        copyBtn.addEventListener('click', () => {
            const temp = document.createElement('div');
            temp.innerHTML = codeSnippets[currentTab];
            const rawText = temp.textContent || temp.innerText;

            navigator.clipboard.writeText(rawText).then(() => {
                copyBtn.innerHTML = `<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-400"></i><span class="text-emerald-400">Copied!</span>`;
                if (window.lucide) window.lucide.createIcons();
                setTimeout(() => {
                    copyBtn.innerHTML = `<i data-lucide="copy" class="w-3.5 h-3.5"></i><span>Copy</span>`;
                    if (window.lucide) window.lucide.createIcons();
                }, 2000);
            });
        });
    }
}

// ==========================================
// 4. Live Animated Architecture Pipeline Flow
// ==========================================
const flowSteps = [
    {
        title: "1. Pod Crash & OOM Kill",
        log: "14:02:19.412 [k8s-kernel] Pod checkout-service-7bb8c-x9k2p Terminated with exitCode: 137 (cgroup OOM)",
        color: "text-rose-400"
    },
    {
        title: "2. Prometheus Alertmanager Fire",
        log: "14:02:20.104 [alertmanager] Firing alert KubePodContainerOOMKilled severity=critical namespace=payments-prod",
        color: "text-amber-400"
    },
    {
        title: "3. Causa Inbound Webhook",
        log: "14:02:20.450 [webhook-controller] POST /api/v1/webhooks/alerts -> Session #CS-8942-RCA initialized",
        color: "text-cyan-400"
    },
    {
        title: "4. MCP Parallel Tool Gathering",
        log: "14:02:21.890 [mcp-client] Querying Kubernetes MCP, Cryostat JFR MCP & Kruize Autotune MCP via JSON-RPC 2.0",
        color: "text-purple-400"
    },
    {
        title: "5. LLM Prompt Reasoning",
        log: "14:02:24.310 [langchain4j] PromptTemplate rendered with 6 diagnostic signals -> Claude 3.7 Sonnet (temp=0.1)",
        color: "text-indigo-400"
    },
    {
        title: "6. Root Cause Validation",
        log: "14:02:27.140 [rca-engine] Corroborating JFR Old Gen Heap allocation spike (96%) with Kernel dmesg RSS limit breach",
        color: "text-amber-400"
    },
    {
        title: "7. Structured RCA Report",
        log: "14:02:28.020 [rca-generator] Generated dual-tier JSON diagnosis: confidence=0.98, immediate=kubectl patch limits=2048Mi",
        color: "text-cyan-400"
    },
    {
        title: "8. Agentic IDE Auto-Fix",
        log: "14:02:29.500 [ide-agent] Bob IDE / Claude Code received RCA report via Causa MCP skill -> Auto-mitigation applied in 88s!",
        color: "text-emerald-400"
    }
];

function initArchitectureFlowAnimation() {
    const cards = document.querySelectorAll('.flow-step-card');
    const progressBar = document.getElementById('flow-progress-bar');
    const label = document.getElementById('flow-active-label');
    const logDisplay = document.getElementById('flow-terminal-log');
    const counter = document.getElementById('flow-step-counter');
    const playPauseBtn = document.getElementById('flow-play-pause-btn');
    const resetBtn = document.getElementById('flow-reset-btn');
    const btnIcon = document.getElementById('flow-btn-icon');
    const btnText = document.getElementById('flow-btn-text');

    if (!cards.length) return;

    let currentStep = 0;
    let isPlaying = true;
    let timer = null;

    function activateStep(index) {
        currentStep = index;

        cards.forEach((card, idx) => {
            if (idx === index) {
                card.classList.add('active-flow-node', 'pulse-ring-active');
                card.classList.remove('completed-flow-node');
            } else if (idx < index) {
                card.classList.remove('active-flow-node', 'pulse-ring-active');
                card.classList.add('completed-flow-node');
            } else {
                card.classList.remove('active-flow-node', 'pulse-ring-active', 'completed-flow-node');
            }
        });

        const stepData = flowSteps[index];
        if (label) label.textContent = stepData.title;
        if (logDisplay) {
            logDisplay.textContent = stepData.log;
            logDisplay.className = `${stepData.color} truncate`;
        }
        if (counter) counter.textContent = (index + 1);

        if (progressBar) {
            const pct = ((index + 1) / flowSteps.length) * 100;
            progressBar.style.width = `${pct}%`;
        }
    }

    function nextStep() {
        let next = (currentStep + 1) % flowSteps.length;
        activateStep(next);
    }

    function startTimer() {
        if (timer) clearInterval(timer);
        timer = setInterval(nextStep, 2600);
        isPlaying = true;
        if (btnText) btnText.textContent = 'Pause Live Animation';
        if (btnIcon) btnIcon.setAttribute('data-lucide', 'pause');
        if (window.lucide) window.lucide.createIcons();
    }

    function stopTimer() {
        if (timer) clearInterval(timer);
        timer = null;
        isPlaying = false;
        if (btnText) btnText.textContent = 'Resume Live Animation';
        if (btnIcon) btnIcon.setAttribute('data-lucide', 'play');
        if (window.lucide) window.lucide.createIcons();
    }

    if (playPauseBtn) {
        playPauseBtn.addEventListener('click', () => {
            if (isPlaying) {
                stopTimer();
            } else {
                startTimer();
            }
        });
    }

    if (resetBtn) {
        resetBtn.addEventListener('click', () => {
            activateStep(0);
            if (!isPlaying) startTimer();
        });
    }

    // Allow user to click any node to jump directly to it
    cards.forEach(card => {
        card.addEventListener('click', () => {
            const stepIdx = parseInt(card.getAttribute('data-step'), 10);
            if (!isNaN(stepIdx)) {
                activateStep(stepIdx);
                // Restart cycle from this step
                if (isPlaying) {
                    clearInterval(timer);
                    timer = setInterval(nextStep, 2600);
                }
            }
        });
    });

    // Start flow
    activateStep(0);
    startTimer();
}
