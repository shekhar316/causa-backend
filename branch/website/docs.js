/**
 * Causa AI — Dedicated Docs Hub Page Controller
 */

document.addEventListener('DOMContentLoaded', () => {
    if (window.lucide) {
        window.lucide.createIcons();
    }

    initDocsPage();
});

function initDocsPage() {
    const sidebarNav = document.getElementById('docs-sidebar-nav');
    const contentArticle = document.getElementById('docs-article');
    const categoryDisplay = document.getElementById('doc-breadcrumb-category');
    const pathDisplay = document.getElementById('doc-breadcrumb-path');
    const searchInput = document.getElementById('doc-search-input');
    const copyRawBtn = document.getElementById('copy-doc-raw-btn');
    const footerNav = document.getElementById('doc-footer-nav');
    const container = document.getElementById('docs-content-container');

    if (!window.CAUSA_DOCS) return;

    // Helper: generate consistent heading slug (matches GitHub Markdown standard)
    function slugify(text) {
        if (!text) return '';
        return text
            .toString()
            .replace(/<[^>]*>/g, '') // remove HTML tags
            .trim()
            .toLowerCase()
            .replace(/[^\w\s-]/g, '') // remove special punctuation (quotes, ?, !, etc)
            .replace(/[\s]+/g, '-') // spaces to single hyphen
            .replace(/-+/g, '-'); // deduplicate hyphens
    }

    // Configure Marked
    if (typeof marked !== 'undefined') {
        const renderer = new marked.Renderer();

        // Custom heading renderer that sets standard IDs
        renderer.heading = function({ text, depth, raw }) {
            const headingText = raw || text || '';
            const id = slugify(headingText);
            return `<h${depth} id="${id}">${text}</h${depth}>\n`;
        };

        // Custom link renderer fallback
        renderer.link = function({ href, title, text }) {
            const titleAttr = title ? ` title="${title}"` : '';
            return `<a href="${href}"${titleAttr}>${text}</a>`;
        };

        marked.use({
            renderer: renderer,
            gfm: true,
            breaks: false
        });
    }

    const docs = window.CAUSA_DOCS;
    const docKeys = Object.keys(docs);
    
    // Group by category
    const categories = {};
    docKeys.forEach(id => {
        const doc = docs[id];
        const cat = doc.category || 'General';
        if (!categories[cat]) categories[cat] = [];
        categories[cat].push({ id, ...doc });
    });

    let currentDocId = 'root-readme';

    // Parse URL param or hash e.g. docs.html?doc=docs_api_README_md or #about-the-project
    const urlParams = new URLSearchParams(window.location.search);
    const requestedDoc = urlParams.get('doc');
    const hash = window.location.hash.replace('#', '');

    if (requestedDoc && docs[requestedDoc]) {
        currentDocId = requestedDoc;
    } else if (hash && docs[hash]) {
        currentDocId = hash;
    }

    function renderSidebar(filterQuery = '') {
        sidebarNav.innerHTML = '';
        const q = filterQuery.toLowerCase().trim();

        Object.keys(categories).forEach(categoryName => {
            const items = categories[categoryName].filter(item => {
                if (!q) return true;
                return item.title.toLowerCase().includes(q) || 
                       item.path.toLowerCase().includes(q) || 
                       item.content.toLowerCase().includes(q);
            });

            if (items.length === 0) return;

            const groupDiv = document.createElement('div');
            groupDiv.className = 'space-y-1';

            const catHeader = document.createElement('div');
            catHeader.className = 'text-[11px] font-mono font-bold uppercase tracking-wider text-slate-500 px-3 py-1 flex items-center justify-between';
            catHeader.innerHTML = `<span>${categoryName}</span><span class="text-[10px] text-slate-600">(${items.length})</span>`;
            groupDiv.appendChild(catHeader);

            items.forEach(doc => {
                const btn = document.createElement('button');
                const isActive = doc.id === currentDocId;
                btn.className = `doc-nav-item w-full text-left px-3 py-2 rounded-lg text-xs font-mono transition-all flex items-center justify-between ${
                    isActive ? 'active bg-cyan-500/20 text-cyan-300 border-l-2 border-cyan-400 font-semibold' : 'text-slate-300 hover:text-white hover:bg-slate-800/80'
                }`;
                btn.setAttribute('data-doc-id', doc.id);
                btn.innerHTML = `
                    <span class="truncate pr-2">${doc.title}</span>
                    <i data-lucide="chevron-right" class="w-3.5 h-3.5 ${isActive ? 'text-cyan-400' : 'text-slate-600'} flex-shrink-0"></i>
                `;
                btn.addEventListener('click', () => {
                    selectDoc(doc.id);
                });
                groupDiv.appendChild(btn);
            });

            sidebarNav.appendChild(groupDiv);
        });

        if (window.lucide) window.lucide.createIcons();
    }

    function selectDoc(docId, updateHistory = true, targetAnchor = null) {
        const doc = docs[docId];
        if (!doc) return;

        currentDocId = docId;

        // Update URL
        if (updateHistory) {
            const newUrl = targetAnchor ? `docs.html?doc=${docId}#${targetAnchor}` : `docs.html?doc=${docId}`;
            history.pushState(null, '', newUrl);
        }

        // Active State in Sidebar
        document.querySelectorAll('.doc-nav-item').forEach(el => {
            if (el.getAttribute('data-doc-id') === docId) {
                el.classList.add('active', 'bg-cyan-500/20', 'text-cyan-300', 'border-l-2', 'border-cyan-400', 'font-semibold');
                el.classList.remove('text-slate-300');
            } else {
                el.classList.remove('active', 'bg-cyan-500/20', 'text-cyan-300', 'border-l-2', 'border-cyan-400', 'font-semibold');
                el.classList.add('text-slate-300');
            }
        });

        // Breadcrumbs & Meta
        if (categoryDisplay) categoryDisplay.textContent = doc.category;
        if (pathDisplay) pathDisplay.textContent = doc.path;

        // Render Markdown safely
        if (typeof marked !== 'undefined') {
            try {
                contentArticle.innerHTML = marked.parse(doc.content);
            } catch (err) {
                console.error("Marked parse error:", err);
                contentArticle.innerHTML = `<pre>${doc.content}</pre>`;
            }
        } else {
            contentArticle.innerHTML = `<pre>${doc.content}</pre>`;
        }

        // Highlight code
        if (typeof hljs !== 'undefined') {
            contentArticle.querySelectorAll('pre code').forEach((block) => {
                try {
                    hljs.highlightElement(block);
                } catch (e) {}
            });
        }

        // Attach smart click interceptors to every link
        bindArticleLinks(doc);

        // Build Prev/Next footer navigation
        renderFooterNav(docId);

        // Scroll to target anchor if specified, else scroll to top
        if (targetAnchor) {
            setTimeout(() => scrollToAnchor(targetAnchor), 50);
        } else if (container) {
            container.scrollTop = 0;
        }
    }

    /**
     * Intercept and route clicks on links within the rendered article
     */
    function bindArticleLinks(currentDoc) {
        contentArticle.querySelectorAll('a').forEach(anchor => {
            const href = anchor.getAttribute('href');
            if (!href) return;

            // 1. GitHub repo links pointing to docs or subdirectories in causaai/causa
            // e.g. https://github.com/causaai/causa/tree/main/docs/api
            const ghDocsMatch = href.match(/^https?:\/\/github\.com\/causaai\/causa\/(?:tree|blob)\/[^\/]+\/(.*)$/i);
            if (ghDocsMatch) {
                const subpath = ghDocsMatch[1];
                const matchedKey = resolveDocKeyFromHref(subpath, currentDoc.path);
                if (matchedKey) {
                    anchor.style.cursor = 'pointer';
                    anchor.addEventListener('click', (e) => {
                        e.preventDefault();
                        selectDoc(matchedKey);
                    });
                    return;
                }
            }

            // 2. Internal in-page anchor links (e.g. #about-the-project, #readme-top)
            if (href.startsWith('#')) {
                anchor.style.cursor = 'pointer';
                anchor.addEventListener('click', (e) => {
                    e.preventDefault();
                    const rawAnchor = href.replace('#', '');
                    scrollToAnchor(rawAnchor);
                    history.pushState(null, '', `docs.html?doc=${currentDocId}#${rawAnchor}`);
                });
                return;
            }

            // 3. Relative or internal documentation links (e.g. alerts-api.md, docs/api/README.md, ./mcp.md#layer)
            if (!href.startsWith('http://') && !href.startsWith('https://') && !href.startsWith('mailto:')) {
                const parts = href.split('#');
                const filePart = parts[0];
                const anchorPart = parts[1] || null;

                const matchedKey = resolveDocKeyFromHref(filePart, currentDoc.path);
                if (matchedKey) {
                    anchor.style.cursor = 'pointer';
                    anchor.addEventListener('click', (e) => {
                        e.preventDefault();
                        selectDoc(matchedKey, true, anchorPart);
                    });
                }
            }
        });
    }

    /**
     * Smoothly scroll to heading by ID or slugified text
     */
    function scrollToAnchor(rawAnchorId) {
        if (!rawAnchorId) return;

        if (rawAnchorId === 'readme-top' || rawAnchorId === 'top') {
            if (container) container.scrollTo({ top: 0, behavior: 'smooth' });
            return;
        }

        const slugId = slugify(rawAnchorId);
        
        // Match elements by exact ID, slug ID, name attribute, or heading text
        const targetEl = document.getElementById(rawAnchorId) || 
                         document.getElementById(slugId) || 
                         contentArticle.querySelector(`[id="${rawAnchorId}"]`) ||
                         contentArticle.querySelector(`[id="${slugId}"]`) ||
                         contentArticle.querySelector(`[name="${rawAnchorId}"]`) ||
                         findHeadingByText(rawAnchorId);

        if (targetEl) {
            targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    /**
     * Fallback finder: locate heading by text similarity
     */
    function findHeadingByText(rawText) {
        const clean = rawText.replace(/[-_]/g, ' ').trim().toLowerCase();
        const headings = contentArticle.querySelectorAll('h1, h2, h3, h4, h5, h6');
        for (const h of headings) {
            const hText = h.textContent.trim().toLowerCase();
            if (hText === clean || hText.includes(clean) || clean.includes(hText)) {
                return h;
            }
        }
        return null;
    }

    /**
     * Resolves relative paths, filenames, or directory names to keys in CAUSA_DOCS
     */
    function resolveDocKeyFromHref(href, currentDocPath) {
        let clean = href.split('#')[0].split('?')[0].replace(/^\.\//, '').replace(/\/$/, '').trim();
        if (!clean) return null;

        // If link refers to root "docs" directory
        if (clean === 'docs') {
            return 'root-readme';
        }

        // Direct matching variants
        const candidates = [
            clean,
            `docs/${clean}`,
            `${clean}/README.md`,
            `docs/${clean}/README.md`,
            clean.endsWith('.md') ? clean : `${clean}.md`,
            clean.endsWith('.md') ? `docs/${clean}` : `docs/${clean}.md`
        ];

        for (const cand of candidates) {
            for (const k of docKeys) {
                if (docs[k].path.toLowerCase() === cand.toLowerCase()) {
                    return k;
                }
            }
        }

        // Relative path calculation against current file's directory
        const currentDir = currentDocPath.includes('/') 
            ? currentDocPath.substring(0, currentDocPath.lastIndexOf('/')) 
            : '';
            
        let combined = currentDir ? `${currentDir}/${clean}` : clean;
        
        // Normalize ../ and ./
        const segs = combined.split('/');
        const norm = [];
        for (const s of segs) {
            if (s === '..') {
                if (norm.length > 0) norm.pop();
            } else if (s !== '.' && s !== '') {
                norm.push(s);
            }
        }
        const relPath = norm.join('/');
        const relCandidates = [
            relPath,
            `${relPath}/README.md`,
            relPath.endsWith('.md') ? relPath : `${relPath}.md`
        ];

        for (const cand of relCandidates) {
            for (const k of docKeys) {
                if (docs[k].path.toLowerCase() === cand.toLowerCase()) {
                    return k;
                }
            }
        }

        // Match by filename only
        const filename = clean.substring(clean.lastIndexOf('/') + 1).toLowerCase();
        for (const k of docKeys) {
            const docFile = docs[k].path.substring(docs[k].path.lastIndexOf('/') + 1).toLowerCase();
            if (docFile === filename || docFile === `${filename}.md`) {
                return k;
            }
        }

        return null;
    }

    function renderFooterNav(docId) {
        const index = docKeys.indexOf(docId);
        const prevDoc = index > 0 ? docs[docKeys[index - 1]] : null;
        const nextDoc = index < docKeys.length - 1 ? docs[docKeys[index + 1]] : null;

        let html = '';
        if (prevDoc) {
            html += `
                <button class="doc-prev-btn flex items-center space-x-2 text-left p-3 rounded-xl bg-slate-900/60 border border-white/10 hover:border-cyan-500/40 transition-all group">
                    <i data-lucide="arrow-left" class="w-4 h-4 text-cyan-400 group-hover:-translate-x-1 transition-transform"></i>
                    <div>
                        <div class="text-[10px] font-mono uppercase text-slate-500">Previous Document</div>
                        <div class="text-xs font-mono font-medium text-slate-200 group-hover:text-cyan-300">${prevDoc.title}</div>
                    </div>
                </button>
            `;
        } else {
            html += `<div></div>`;
        }

        if (nextDoc) {
            html += `
                <button class="doc-next-btn flex items-center space-x-2 text-right p-3 rounded-xl bg-slate-900/60 border border-white/10 hover:border-cyan-500/40 transition-all group">
                    <div>
                        <div class="text-[10px] font-mono uppercase text-slate-500">Next Document</div>
                        <div class="text-xs font-mono font-medium text-slate-200 group-hover:text-cyan-300">${nextDoc.title}</div>
                    </div>
                    <i data-lucide="arrow-right" class="w-4 h-4 text-cyan-400 group-hover:translate-x-1 transition-transform"></i>
                </button>
            `;
        }

        footerNav.innerHTML = html;

        if (prevDoc) {
            footerNav.querySelector('.doc-prev-btn').addEventListener('click', () => selectDoc(docKeys[index - 1]));
        }
        if (nextDoc) {
            footerNav.querySelector('.doc-next-btn').addEventListener('click', () => selectDoc(docKeys[index + 1]));
        }

        if (window.lucide) window.lucide.createIcons();
    }

    // Copy raw markdown button
    if (copyRawBtn) {
        copyRawBtn.addEventListener('click', () => {
            const currentContent = docs[currentDocId] ? docs[currentDocId].content : '';
            navigator.clipboard.writeText(currentContent).then(() => {
                copyRawBtn.innerHTML = `<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-400"></i><span class="text-emerald-400">Copied!</span>`;
                if (window.lucide) window.lucide.createIcons();
                setTimeout(() => {
                    copyRawBtn.innerHTML = `<i data-lucide="copy" class="w-3.5 h-3.5"></i><span>Copy Markdown</span>`;
                    if (window.lucide) window.lucide.createIcons();
                }, 2000);
            });
        });
    }

    // Search filter
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            renderSidebar(e.target.value);
        });
    }

    // Popstate handling (browser back/forward)
    window.addEventListener('popstate', () => {
        const urlP = new URLSearchParams(window.location.search);
        const reqDoc = urlP.get('doc');
        const h = window.location.hash.replace('#', '');
        if (reqDoc && docs[reqDoc]) {
            selectDoc(reqDoc, false, h || null);
        } else if (h && docs[h]) {
            selectDoc(h, false);
        }
    });

    // Initial render
    renderSidebar();
    selectDoc(currentDocId, false, hash || null);
}
