/* =====================================================================
   hoho_activity_render.js  -  호호책방 활동지 JSON → HTML 렌더러 (독립 사용, jQuery 불필요)
   hoho_activity_sheet.asp 의 hsRender / hsQHtml / hsHead / hsFoot / hsPrintData 와 같은 결과를 낸다.
   함께 필요한 파일: hoho_activity_sheet.css

   사용법
     <link rel="stylesheet" href="/hoho/hoho_activity_sheet.css">
     <script src="/hoho/hoho_activity_render.js"></script>

     HohoSheet.mount('#box', data, { teacher:false, zoom:true });   // 화면에 표시
     HohoSheet.print(data, { teacher:true });                         // 새 창 → 인쇄(PDF로 저장)
     const html = HohoSheet.render(data, { teacher:false });          // HTML 문자열만 필요할 때

   data 로 넣을 수 있는 것 (셋 다 자동 인식)
     1) erp_hoho_activity_result.json_data 컬럼 값 그대로 (문자열)
     2) 그것을 JSON.parse 한 객체
     3) hoho_activity_api.asp?action=result_get 응답 전체 ({success, found, json:"..."})
   ===================================================================== */
(function (global) {
    'use strict';

    // ---------- 설정 (필요하면 HohoSheet.config 로 바꾼다) ----------
    var config = {
        logoUrl: '/images/logo_chaekbang.png',   // 하단 로고 이미지 URL. 비우면 글자 로고(호호책방) — book_clinic 2026-11-11
        cssUrl: '',               // 인쇄 창에 넣을 CSS 주소. 비우면 이 스크립트와 같은 폴더의 hoho_activity_sheet.css
        theme: {                  // 학년별 머리띠 색 (예제 PDF 색상)
            1: { head: '#F5E877', pill: '#F2B26B', pillText: '#3a2a10' },
            2: { head: '#A6E2F9', pill: '#2E7FA0', pillText: '#ffffff' },
            3: { head: '#BAE2C7', pill: '#6DB68C', pillText: '#ffffff' }
        },
        badge: { hanja: '#d75ab4', grammar: '#2c3f8a' },
        pill:  { hanja: '#d955b2', grammar: '#1b4683' },
        ban:   { hanja: ['#d3ecf7', '#4a6fa5'], grammar: ['#f4cbe4', '#c0487f'] }
    };

    // 스크립트 위치로 CSS 기본 경로를 잡아 둔다
    var scriptSrc = (document.currentScript && document.currentScript.src) || '';
    var defaultCss = scriptSrc ? scriptSrc.replace(/[^\/?#]*([?#].*)?$/, '') + 'hoho_activity_sheet.css' : 'hoho_activity_sheet.css';

    // ---------- 문자열 도우미 ----------
    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c];
        });
    }
    // <u></u> 만 살리고 나머지는 이스케이프, 줄바꿈은 <br>
    function T(s) { return esc(s).replace(/&lt;u&gt;/g, '<u>').replace(/&lt;\/u&gt;/g, '</u>').replace(/\n/g, '<br>'); }
    function lead(s) { return T(s).replace(/\(\s*\)/g, '(<span class="hs-pb"></span>)'); }
    function blankText(s) { return T(s).split('[빈칸]').join('(<span class="hs-blank"></span>)'); }
    function arr(v) { return Array.isArray(v) ? v : []; }
    function grade(d) { var g = parseInt(d.grade, 10) || 1; return Math.min(Math.max(g, 1), 3); }

    // ---------- 입력 정리 ----------
    function toData(input) {
        var d = input;
        if (typeof d === 'string') d = JSON.parse(d.replace(/^﻿/, ''));
        if (d && d.success !== undefined && d.json !== undefined) {           // result_get 응답 전체
            if (!d.found) throw new Error('저장된 활동지가 없습니다.');
            d = typeof d.json === 'string' ? JSON.parse(d.json) : d.json;
        }
        if (!d || typeof d !== 'object') throw new Error('활동지 JSON 형식이 아닙니다.');
        d.activity_sheet = d.activity_sheet || { sections: [] };
        d.activity_sheet.sections = arr(d.activity_sheet.sections);
        d.advanced_questions = arr(d.advanced_questions);
        d.book_title = String(d.book_title || '');
        d.theme = String(d.theme || '');
        return d;
    }

    // ---------- 문항 1개 ----------
    function qHtml(q, badgeColor) {
        var h = '<div class="hs-q"><div class="hs-qh"><span class="hs-no" style="background:' + badgeColor + ';">' + esc(q.no) + '</span><span>' + T(q.instruction) + '</span></div>';
        var ex = q.example, i, r, c;
        if (ex && arr(ex.lines).length) {
            h += '<div class="hs-ex' + (ex.lines.length > 1 ? ' multi' : '') + '"><span class="hs-lab">' + esc(ex.label || '예문') + '</span><div class="hs-exl">'
                + ex.lines.map(function (l) { return l === '[빈칸]' ? '<div class="hs-exblank"></div>' : '<div>' + T(l) + '</div>'; }).join('') + '</div></div>';
        }
        if (q.chips && arr(q.chips.items).length) {
            h += '<div class="hs-chips"><span class="hs-lab">' + esc(q.chips.label || '보기') + '</span>'
                + q.chips.items.map(function (x) { return '<div class="hs-chip">' + T(x) + '</div>'; }).join('') + '</div>';
        }
        if (arr(q.pairs).length) {
            var n = q.pairs.length;   // 오른쪽 뜻은 한 칸씩 밀어 섞는다 (왼쪽과 같은 줄에 정답이 놓이지 않게)
            h += '<div class="hs-match">' + q.pairs.map(function (p, k) {
                return '<div class="hs-mw"><span class="hs-mn">' + (k + 1) + ')</span> ' + T(p.word).replace(/\(([^)]+)\)/, '<span class="hs-hj">($1)</span>') + '</div>'
                    + '<div class="hs-md">●</div><div class="hs-md">●</div><div class="hs-mm">' + T(q.pairs[(k + 1) % n].meaning) + '</div>';
            }).join('') + '</div>';
        }
        if (q.blanks && arr(q.blanks.sentences).length) {
            var many = q.blanks.sentences.length > 1;
            h += '<div class="hs-fill' + (many ? '' : ' one') + '">' + q.blanks.sentences.map(function (t, k) {
                return '<div>' + (many ? '①②③④⑤'.charAt(k) + ' ' : '') + blankText(t) + '</div>';
            }).join('') + '</div>';
        }
        if (arr(q.pieces).length) h += '<div class="hs-pieces">' + q.pieces.map(T).join(' &nbsp;/&nbsp; ') + '</div>';
        if (arr(q.fields).length) {
            h += '<div class="hs-fields">' + q.fields.map(function (f) {
                return '<div class="hs-frow"><span class="hs-flab">' + T(f) + '</span><div class="hs-fbox"><div class="hs-fdots"></div></div></div>';
            }).join('') + '</div>';
        }
        if (q.grid) {
            var rows = Math.min(Math.max(parseInt(q.grid.rows, 10) || 2, 1), 4), cols = Math.min(Math.max(parseInt(q.grid.cols, 10) || 13, 5), 16);
            h += '<table class="hs-grid">';
            for (r = 0; r < rows; r++) { h += '<tr>'; for (c = 0; c < cols; c++) h += '<td></td>'; h += '</tr>'; }
            h += '</table>';
        }
        if (q.hint || q.gloss) {
            h += '<div class="hs-note' + (q.hint && q.gloss ? ' both' : '') + '">' + (q.hint ? '<span class="hs-hint">' + T(q.hint) + '</span>' : '')
                + (q.gloss ? '<span>• ' + T(q.gloss) + '</span>' : '') + '</div>';
        }
        var ab = q.answer_box;
        if (!ab && !arr(q.pairs).length && !q.blanks && !arr(q.fields).length && !q.grid) ab = { lead: '', tail: '', lines: 1 };   // 쓸 칸이 없으면 기본 답란
        if (ab) {
            var lines = Math.min(Math.max(parseInt(ab.lines, 10) || 1, 1), 4);
            h += '<div class="hs-abox">';
            for (i = 0; i < lines; i++) {
                var ld = (i === 0 && ab.lead) ? '<span>' + lead(ab.lead) + '</span>' : '';
                var tl = (i === lines - 1 && ab.tail) ? '<span class="hs-tailt">' + lead(ab.tail) + '</span>' : '';
                // 앞말·뒷말이 있으면 그 글자 옆만 점선으로 잇는다 (기존 PDF 활동지와 같게, 2026-11-11)
                h += (tl || ld) ? '<div class="hs-aline hs-has-tail">' + ld + '<span class="hs-dotspan"></span>' + tl + '</div>' : '<div class="hs-aline"></div>';
            }
            h += '</div>';
        }
        return h + '</div>';
    }

    // ---------- 머리띠 / 바닥 ----------
    function head(d, kicker, noName) {
        var th = config.theme[grade(d)], len = d.book_title.length;
        var fs = len <= 10 ? 30 : len <= 14 ? 26 : len <= 18 ? 22 : 19;
        return '<div class="hs-head" style="background:' + th.head + ';"><div class="hs-head-l"><div class="hs-kicker">' + esc(kicker) + '</div>'
            + '<div class="hs-title" style="font-size:' + fs + 'px;">「&nbsp;' + esc(d.book_title) + '&nbsp;」</div>'
            + '<div class="hs-theme"><span class="hs-tp" style="background:' + th.pill + ';color:' + th.pillText + ';">주제</span><span>' + esc(d.theme) + '</span></div></div>'
            + (noName ? '' : '<div class="hs-head-r"><div class="hs-date"><span>월</span><span>일</span></div><div class="hs-name">이름 :</div></div>') + '</div>';
    }
    function foot(left) {
        var logo = config.logoUrl ? '<img src="' + esc(config.logoUrl) + '" alt="호호책방">' : '<span style="color:#2c6fb3;">호호</span><span style="color:#f08a24;">책방</span>';
        return '<div class="hs-foot">' + left + '<div class="hs-logo">' + logo + '</div></div>';
    }
    var CHECK = '<div class="hs-check"><div class="hs-cl">선생님 확인</div></div>';

    function ansText(q) {
        var parts = [], b = q.blanks;
        if (arr(q.pairs).length) parts.push(q.pairs.map(function (p) { return p.word + ' → ' + p.meaning; }).join(' / '));
        if (b && arr(b.answers).length) parts.push(b.answers.map(function (a, i) { return (b.answers.length > 1 ? '①②③④⑤'.charAt(i) + ' ' : '') + a; }).join(', '));
        if (q.answer && !arr(q.pairs).length) parts.push(q.answer);
        return parts.join(' ') || '-';
    }

    // ---------- 전체 문서 ----------
    function render(input, opts) {
        opts = opts || {};
        var d = toData(input), g = grade(d), teacher = !!opts.teacher;
        var secs = d.activity_sheet.sections, adv = d.advanced_questions;
        var showAdv = opts.advanced !== false && adv.length > 0;
        var h = '<div class="hs-doc">';

        // 1) 활동지 (A4 한 장)
        h += '<section class="hs-page">' + head(d, g + '학년 문해력 확장 활동') + '<div class="hs-body">';
        secs.forEach(function (sec) {
            var id = config.pill[sec.id] ? sec.id : 'grammar';
            h += '<div class="hs-card"><div class="hs-sechead"><span class="hs-pill" style="background:' + config.pill[id] + ';">' + esc(sec.title) + '</span>'
                + '<span class="hs-ban" style="background:' + config.ban[id][0] + ';color:' + config.ban[id][1] + ';">' + esc(sec.banner) + '</span></div>'
                + arr(sec.questions).map(function (q) { return qHtml(q, config.badge[id]); }).join('') + '</div>';
        });
        h += '</div>' + foot(CHECK) + '</section>';

        // 2) 영역별 심화문제
        if (showAdv) {
            h += '<section class="hs-page">' + head(d, g + '학년 영역별 심화문제') + '<div class="hs-body">';
            var cur = '', n = 0;
            adv.forEach(function (q) {
                if (q.area !== cur) { cur = q.area; h += '<div><span class="hs-area">' + esc(cur) + '</span></div>'; }
                n++;
                h += '<div class="hs-aq"><div class="hs-qh"><span class="hs-no" style="background:#2c3f8a;">' + n + '</span><span>' + T(q.q) + '</span></div>';
                if (q.qex) h += '<div class="hs-ex" style="margin-left:32px;"><span class="hs-lab">예문</span><div class="hs-exl" style="text-align:left;">' + T(q.qex) + '</div></div>';
                h += '<div class="hs-choices">' + arr(q.choices).map(function (c, i) { return '<div>' + '①②③④'.charAt(i) + ' ' + T(c) + '</div>'; }).join('') + '</div></div>';
            });
            h += '</div>' + foot(CHECK) + '</section>';
        }

        // 3) 교사용 정답
        if (teacher) {
            h += '<section class="hs-page">' + head(d, g + '학년 정답 및 해설 (교사용)', true) + '<div class="hs-body">';
            h += '<div><span class="hs-area">활동지 정답 · 예시 답안</span></div><table class="hs-ans-tbl"><tr><th style="width:70px;">영역</th><th style="width:36px;">번호</th><th>정답 / 예시 답안</th></tr>'
                + secs.map(function (sec) {
                    return arr(sec.questions).map(function (q) {
                        return '<tr><td>' + esc(sec.title) + '</td><td style="text-align:center;">' + esc(q.no) + '</td><td>' + T(ansText(q)) + '</td></tr>';
                    }).join('');
                }).join('') + '</table>';
            if (showAdv) {
                h += '<div><span class="hs-area">심화문제 정답</span></div><table class="hs-ans-tbl"><tr><th style="width:36px;">번호</th><th style="width:100px;">영역</th><th style="width:130px;">세부유형</th><th style="width:40px;">정답</th><th>해설</th></tr>'
                    + adv.map(function (q, i) {
                        return '<tr><td style="text-align:center;">' + (i + 1) + '</td><td>' + esc(q.area) + '</td><td>' + esc(q.type) + '</td><td style="text-align:center;font-weight:700;">'
                            + ('①②③④'.charAt((parseInt(q.ans, 10) || 0) - 1) || '-') + '</td><td>' + (T(q.commentary) || '-') + '</td></tr>';
                    }).join('') + '</table>';
            }
            h += '</div>' + foot('<span></span>') + '</section>';
        }
        return h + '</div>';
    }

    // ---------- 넘침 맞추기 (book_clinic 2026-11-11) ----------
    // 장은 A4 높이로 고정이라 문항이 많으면 아래가 잘린다. 본문(.hs-body)이 바닥(선생님 확인) 위까지
    // 들어가지 않으면 그 장의 본문만 비율대로 줄인다. 문서가 화면에 붙고 CSS·글꼴이 적용된 뒤에 불러야 한다.
    // shortcut: 많이 넘치면(0.7배 미만) 글씨가 작아져도 그대로 줄인다 — 그런 JSON이 나오면 장 나눔을 검토
    function fit(root) {
        var pages = (root || document).querySelectorAll('.hs-page');
        Array.prototype.forEach.call(pages, function (page) {
            var body = page.querySelector('.hs-body'), foot = page.querySelector('.hs-foot');
            if (!body || !foot) return;
            body.style.zoom = '';
            var room = foot.offsetTop - body.offsetTop - 8, need = body.offsetHeight;
            if (need > room && room > 0) body.style.zoom = String(Math.floor(room / need * 1000) / 1000);
        });
        return root;
    }

    // ---------- 화면에 붙이기 ----------
    function mount(target, input, opts) {
        opts = opts || {};
        var el = typeof target === 'string' ? document.querySelector(target) : target;
        if (!el) throw new Error('출력할 요소를 찾지 못했습니다: ' + target);
        var html = render(input, opts);
        el.innerHTML = opts.zoom ? '<div class="hs-zoom">' + html + '</div>' : html;
        fit(el);
        if (document.fonts) document.fonts.ready.then(function () { fit(el); });   // 글꼴이 늦게 오면 다시 잰다
        return el;
    }

    // ---------- 인쇄(PDF 저장) : 새 창에 A4 문서만 만들어 인쇄 ----------
    // 팝업 차단을 피하려면 클릭 이벤트 안에서 바로 호출한다. 비동기로 불러온 뒤 인쇄하려면 opts.win 에 미리 연 창을 넘긴다.
    function print(input, opts) {
        opts = opts || {};
        var d = toData(input);
        var w = opts.win || window.open('', '_blank');
        if (!w) { alert('팝업이 차단되어 인쇄 창을 열 수 없습니다. 팝업을 허용해 주세요.'); return; }
        var name = String(d.content_id || 'activity') + (opts.teacher ? '_교사용' : '');   // 인쇄 창 제목 = PDF 기본 파일명
        var css = config.cssUrl || defaultCss;
        w.document.open();
        w.document.write('<!DOCTYPE html><html lang="ko"><head><meta charset="UTF-8"><title>' + esc(name) + '</title>'
            + '<link rel="stylesheet" href="' + esc(css) + '">'
            + '<style>html,body{margin:0;padding:0;} .hs-page{margin:0;box-shadow:none;}</style>'
            + '</head><body>' + render(d, opts) + '</body></html>');
        w.document.close();
        var done = false;
        function go() { if (done) return; done = true; try { fit(w.document); w.focus(); w.print(); } catch (e) {} }
        w.onload = go;                    // CSS 까지 읽힌 뒤
        setTimeout(go, 1500);             // onload 가 안 오는 브라우저 대비
        return w;
    }

    global.HohoSheet = { config: config, render: render, mount: mount, print: print, fit: fit, parse: toData };
})(window);
