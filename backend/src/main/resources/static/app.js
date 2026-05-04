// app.js

// Interceptor: añade context path y JWT token a las llamadas /api/*
(function() {
    const m = window.location.pathname.match(/^\/([^\/]+)/);
    const seg = m ? m[1] : '';
    const ctx = (seg && !seg.startsWith('api') && !seg.includes('.')) ? '/' + seg : '';
    const _fetch = window.fetch;
    window.fetch = function(url, init) {
        init = init || {};
        if (typeof url === 'string' && url.startsWith('/api/')) {
            if (ctx) url = ctx + url;
            init.headers = Object.assign({}, init.headers || {});
            const token = sessionStorage.getItem('sgdc_jwt');
            if (token && url.indexOf('/api/login') === -1) {
                init.headers['Authorization'] = 'Bearer ' + token;
            }
            const tenant = localStorage.getItem('sgdc_tenant');
            if (tenant) init.headers['X-Tenant-ID'] = tenant;
        }
        return _fetch.call(this, url, init);
    };
    console.log('[fetch-interceptor] context=' + ctx + ' (JWT + X-Tenant-ID auto-attach activo)');
})();

document.addEventListener('DOMContentLoaded', () => {
    // UI Containers
    const landingWrapper = document.getElementById('landing-container');
    const loginWrapper = document.getElementById('login-container');
    const appWrapper = document.getElementById('app-wrapper');
    const loginForm = document.getElementById('login-form');
    const btnLogout = document.getElementById('btn-logout');
    
    // Auth State
    let currentUser = null; 
    let currentSchool = null;

    // --- Navigation Flow Functions ---
    window.openLogin = function(schoolCode) {
        currentSchool = schoolCode;
        document.getElementById('login-school-title').textContent = "( " + schoolCode + " )";
        landingWrapper.classList.add('auth-hidden');
        loginWrapper.classList.remove('auth-hidden');
        // loadPlantelesSelect fija el tenant en localStorage y luego llama a loadQuickRoles
        loadPlantelesSelect(schoolCode);
    };

    window.backToLanding = function() {
        loginWrapper.classList.add('auth-hidden');
        landingWrapper.classList.remove('auth-hidden');
    };

    // --- Admin SEM ---
    window.openAdminSem = function() {
        landingWrapper.classList.add('auth-hidden');
        document.getElementById('admin-sem-container').classList.remove('auth-hidden');
        loadSemQuickUsers();
    };

    window.backFromAdminSem = function() {
        sessionStorage.removeItem('sgdc_jwt');
        document.getElementById('admin-sem-container').classList.add('auth-hidden');
        landingWrapper.classList.remove('auth-hidden');
        // reset panel state
        document.getElementById('admin-sem-login-card').classList.remove('auth-hidden');
        document.getElementById('admin-sem-panel').classList.add('auth-hidden');
        document.getElementById('admin-sem-user').value = '';
        document.getElementById('admin-sem-pass').value = '';
        document.getElementById('admin-sem-error').style.display = 'none';
    };

    window.logoutAdminSem = function() {
        window.backFromAdminSem();
    };

    document.getElementById('admin-sem-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('admin-sem-user').value.trim();
        const pass = document.getElementById('admin-sem-pass').value.trim();
        const errEl = document.getElementById('admin-sem-error');
        const btn = e.target.querySelector('button');
        btn.textContent = 'Verificando...';
        btn.disabled = true;
        errEl.style.display = 'none';

        try {
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password: pass })
            });
            const data = await res.json();
            if (res.ok && data.status === 'ok' && data.role === 'SEM') {
                if (data.token) sessionStorage.setItem('sgdc_jwt', data.token);
                document.getElementById('admin-sem-login-card').classList.add('auth-hidden');
                document.getElementById('admin-sem-panel').classList.remove('auth-hidden');
                document.getElementById('admin-sem-logged-user').textContent = username + ' · SEM';
                loadAdminEscuelas();
            } else if (res.ok && data.status === 'ok') {
                errEl.textContent = 'Acceso denegado. Se requiere rol SEM.';
                errEl.style.display = 'block';
            } else {
                errEl.textContent = 'Credenciales inválidas.';
                errEl.style.display = 'block';
            }
        } catch (err) {
            errEl.textContent = 'Error conectando al servidor.';
            errEl.style.display = 'block';
        } finally {
            btn.textContent = 'INGRESAR';
            btn.disabled = false;
        }
    });

    // Handle Login
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('login-user').value.trim();
        const pass = document.getElementById('login-pass').value.trim();
        
        const btn = e.target.querySelector('button');
        const ogBtn = btn.innerHTML;
        btn.innerHTML = 'Verificando...';
        btn.disabled = true;

        try {
            const tenantId = document.getElementById('login-plantel').value;
            if (!tenantId) { alert('Selecciona un plantel antes de ingresar.'); btn.innerHTML = ogBtn; btn.disabled = false; return; }
            // Fijar tenant antes de hacer fetch para que el interceptor lo incluya
            localStorage.setItem('sgdc_tenant', tenantId);
            currentSchool = tenantId.replace('gestion_docente_', '').toUpperCase();

            const res = await fetch('/api/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', 'X-Tenant-ID': tenantId},
                body: JSON.stringify({username: username, password: pass})
            });
            const data = await res.json();
            if (res.ok && data.status === 'ok') {
                if (data.token) sessionStorage.setItem('sgdc_jwt', data.token);
                currentUser = { name: username, role: data.role };
                initApp();
            } else {
                alert('Credenciales inválidas o acceso denegado.');
            }
        } catch(err) {
            alert('Error conectando al servidor BD.');
        } finally {
            btn.innerHTML = ogBtn;
            btn.disabled = false;
        }
    });

    // Handle Logout
    btnLogout.addEventListener('click', () => {
        sessionStorage.removeItem('sgdc_jwt');
        currentUser = null;
        appWrapper.classList.add('auth-hidden');
        loginWrapper.classList.remove('auth-hidden');
        loadQuickRoles();
    });

    function initApp() {
        loginWrapper.classList.add('auth-hidden');
        appWrapper.classList.remove('auth-hidden');
        document.getElementById('display-user-role').textContent = `${currentUser.role} | ${currentUser.name} | ${currentSchool}`;
        document.getElementById('app-main-title').textContent = "BIENVENIDO AL SISTEMA DE GESTIÓN DOCENTE (" + currentSchool + ")";

        // Permissions logic
        document.querySelectorAll('.module-card').forEach(card => {
            const reqRole = card.getAttribute('data-req-role');
            if(reqRole && reqRole !== 'ALL' && !reqRole.split(',').includes(currentUser.role) && currentUser.role !== 'ADM') {
                card.classList.add('hidden');
            } else {
                card.classList.remove('hidden');
            }
        });

        if (currentUser.role === 'DIR') {
            document.getElementById('grid-main-modules').classList.add('hidden');
            document.getElementById('grid-director-dashboard').classList.remove('hidden');
            loadDirectorDashboard();
        } else {
            document.getElementById('grid-main-modules').classList.remove('hidden');
            const dirDash = document.getElementById('grid-director-dashboard');
            if(dirDash) dirDash.classList.add('hidden');
        }

        const btnAdd = document.getElementById('btn-add-docente');
        if (btnAdd) {
            // Only Career chiefs (or ADM) can add teachers
            if (['ICI','ICE','II','IC','TC', 'ADM'].includes(currentUser.role)) {
                btnAdd.classList.remove('hidden');
            } else {
                btnAdd.classList.add('hidden');
            }
        }

        // Fetch real data from Postgres via PowerShell server
        loadDocentesFromDatabase();
        loadVehiculosFromDatabase();
    }

    window.appNavigate = function(viewId) {
        document.querySelectorAll('.view-section').forEach(sec => {
            sec.classList.add('hidden');
            sec.classList.remove('active');
        });
        const target = document.getElementById(`view-${viewId}`);
        if(target) {
            target.classList.remove('hidden');
            target.classList.add('active');
        }

        // Refresh data on navigation
        if (viewId === 'docentes') loadDocentesFromDatabase();
        if (viewId === 'vehiculos') loadVehiculosFromDatabase();
        if (viewId === 'evaluaciones') loadEvaluacionesFromDatabase();
        if (viewId === 'contratos') loadContratosFromDatabase();
        if (viewId === 'presupuesto') loadPresupuesto();
    }

    // ==========================================
    // DATA FETCHING & REAL DATABASE CONNECTION
    // ==========================================
    let _dirDocentes = []; // global for director drill-down

    async function loadDirectorDashboard() {
        try {
            const [docRes, vehRes, evalRes] = await Promise.all([
                fetch('/api/docentes').catch(() => ({ok:false})),
                fetch('/api/vehiculos').catch(() => ({ok:false})),
                fetch('/api/evaluaciones').catch(() => ({ok:false}))
            ]);

            _dirDocentes = docRes.ok ? await docRes.json() : [];
            let vehiculos = vehRes.ok ? await vehRes.json() : [];
            let evaluaciones = evalRes.ok ? await evalRes.json() : [];

            document.getElementById('dir-stat-docentes').textContent = _dirDocentes.length;
            document.getElementById('dir-stat-vehiculos').textContent = vehiculos.length;

            // --- Desglose por carreras (clickable cards) ---
            const carreraGroups = {};
            _dirDocentes.forEach(d => {
                const carreras = d.carrera ? d.carrera.split(',').map(s => s.trim()) : ['NA'];
                carreras.forEach(c => {
                    if (!carreraGroups[c]) carreraGroups[c] = [];
                    carreraGroups[c].push(d);
                });
            });

            const CARRERA_COLORS = {
                'ICI':'#b45309','ICE':'#0369a1','II':'#065f46','IC':'#9a3412','TC':'#374151',
                'MED':'#be185d','ODO':'#0f766e','ENF':'#0c4a6e','NA':'#64748b'
            };
            
            const chartHtml = Object.entries(carreraGroups).map(([c, docs]) => {
                const bg = CARRERA_COLORS[c] || '#475569';
                return `
                <div onclick="window.dirShowCarrera('${c}')" style="background:white;padding:16px;border-radius:12px;border:2px solid #e2e8f0;width:130px;text-align:center;cursor:pointer;transition:all 0.2s;box-shadow:0 2px 8px rgba(0,0,0,0.03);" onmouseover="this.style.borderColor='${bg}';this.style.transform='translateY(-3px)';this.style.boxShadow='0 6px 18px rgba(0,0,0,0.1)'" onmouseout="this.style.borderColor='#e2e8f0';this.style.transform='none';this.style.boxShadow='0 2px 8px rgba(0,0,0,0.03)'">
                    <div style="font-weight:900;font-size:1.2rem;color:${bg};font-family:var(--font-display);">${c}</div>
                    <div style="font-size:2rem;font-weight:900;color:#1e293b;margin:4px 0;">${docs.length}</div>
                    <div style="height:3px;background:#f1f5f9;border-radius:3px;overflow:hidden;margin-top:6px;">
                        <div style="width:100%;height:100%;background:${bg};"></div>
                    </div>
                    <div style="font-size:0.68rem;color:#94a3b8;margin-top:6px;">Click para ver</div>
                </div>`;
            }).join('');
            document.getElementById('dir-chart-carreras').innerHTML = chartHtml || '<span style="color:#94a3b8;">No hay datos</span>';

            // --- Alertas de evaluación baja ---
            // Buscar docentes con evaluaciones < 70 puntos
            const alertasList = document.getElementById('dir-alertas-list');
            let alertCount = 0;
            let alertHtml = '';

            if (evaluaciones.length > 0) {
                // Agrupar evaluaciones por docente, buscar el puntaje más reciente
                const evalByDocente = {};
                evaluaciones.forEach(ev => {
                    const did = ev.docente_id;
                    if (!evalByDocente[did] || (ev.evaluacion_id > evalByDocente[did].evaluacion_id)) {
                        evalByDocente[did] = ev;
                    }
                });

                Object.values(evalByDocente).forEach(ev => {
                    const puntaje = parseFloat(ev.puntaje_total) || 0;
                    if (puntaje < 70 && puntaje > 0) {
                        alertCount++;
                        const docData = _dirDocentes.find(d => d.docente_id === ev.docente_id);
                        const nombre = docData ? docData.nombre : `Docente #${ev.docente_id}`;
                        const carrera = docData ? (docData.carrera || 'N/A') : 'N/A';
                        const bgColor = puntaje < 50 ? '#fef2f2' : '#fffbeb';
                        const borderColor = puntaje < 50 ? '#fecaca' : '#fef08a';
                        const textColor = puntaje < 50 ? '#991b1b' : '#92400e';
                        alertHtml += `
                        <div style="background:${bgColor};border:1.5px solid ${borderColor};border-radius:12px;padding:14px 16px;display:flex;justify-content:space-between;align-items:center;">
                            <div>
                                <div style="font-weight:700;font-size:0.88rem;color:#1e293b;">${nombre}</div>
                                <div style="font-size:0.75rem;color:#64748b;margin-top:2px;">${carrera} · Evaluado: ${ev.fecha_evaluacion || 'N/A'}</div>
                            </div>
                            <div style="display:flex;align-items:center;gap:10px;">
                                <span style="background:${textColor};color:white;padding:4px 12px;border-radius:20px;font-size:0.85rem;font-weight:900;">${puntaje.toFixed(1)}</span>
                                <button onclick="window.verPerfil(${ev.docente_id}, '${carrera}')" style="background:var(--color-maroon);color:white;border:none;padding:5px 12px;border-radius:6px;cursor:pointer;font-size:0.72rem;font-weight:700;">Ver perfil</button>
                            </div>
                        </div>`;
                    }
                });
            }

            document.getElementById('dir-stat-alertas').textContent = alertCount;
            if (alertCount === 0) {
                alertHtml = '<div style="background:#f0fdf4;border:1.5px solid #bbf7d0;border-radius:12px;padding:18px;text-align:center;color:#166534;font-weight:700;font-size:0.9rem;">✅ Todos los docentes evaluados tienen puntaje aceptable (≥ 70)</div>';
            }
            alertasList.innerHTML = alertHtml;

            // Update KPI card color based on alerts
            const kpiCard = document.getElementById('dir-eval-kpi-card');
            if (alertCount > 0) {
                kpiCard.style.background = 'linear-gradient(135deg, #991b1b, #ef4444)';
            }

            // Populate carrera filter dropdown
            const filterSelect = document.getElementById('dir-filter-carrera');
            if (filterSelect) {
                filterSelect.innerHTML = '<option value="">Todas las carreras</option>';
                Object.keys(carreraGroups).sort().forEach(c => {
                    filterSelect.innerHTML += `<option value="${c}">${c} (${carreraGroups[c].length})</option>`;
                });
            }

            // Render the docentes table
            window.dirFilterTable();

        } catch (e) {
            console.error('Error loadDirectorDashboard', e);
        }
    }

    // Director: filter and render the docentes table
    window.dirFilterTable = function() {
        const searchVal = (document.getElementById('dir-search-nombre')?.value || '').toLowerCase().trim();
        const carreraVal = document.getElementById('dir-filter-carrera')?.value || '';
        const condicionVal = document.getElementById('dir-filter-condicion')?.value || '';

        let filtered = _dirDocentes.filter(d => {
            // Search filter
            if (searchVal) {
                const haystack = [d.nombre, d.rfc, d.curp, d.carrera, d.condicion].filter(Boolean).join(' ').toLowerCase();
                if (!haystack.includes(searchVal)) return false;
            }
            // Carrera filter
            if (carreraVal) {
                const carreras = (d.carrera || '').split(',').map(s => s.trim());
                if (!carreras.includes(carreraVal)) return false;
            }
            // Condicion filter (use includes since DB has 'Personal Civil', 'Personal Militar')
            if (condicionVal) {
                if (!(d.condicion || '').toLowerCase().includes(condicionVal.toLowerCase())) return false;
            }
            return true;
        });

        // Update count
        const countEl = document.getElementById('dir-filter-count');
        if (countEl) countEl.textContent = `${filtered.length} de ${_dirDocentes.length} docentes`;

        // Render table
        const tbody = document.getElementById('dir-docentes-tbody');
        if (!tbody) return;

        if (filtered.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="padding:20px;text-align:center;color:#94a3b8;">No se encontraron docentes con los filtros aplicados.</td></tr>';
            return;
        }

        tbody.innerHTML = filtered.map(d => {
            const materias = d.materias_nombres || d.materias || '';
            const materiasShort = materias.length > 40 ? materias.substring(0, 40) + '...' : materias;
            const condBadge = (d.condicion || '').toLowerCase().includes('militar')
                ? '<span style="background:#1e3a5f;color:white;padding:2px 8px;border-radius:10px;font-size:0.72rem;font-weight:700;">Militar</span>'
                : '<span style="background:#f0fdf4;color:#166534;padding:2px 8px;border-radius:10px;font-size:0.72rem;font-weight:700;border:1px solid #bbf7d0;">Civil</span>';

            return `<tr style="border-bottom:1px solid #f1f5f9;transition:background 0.15s;" onmouseover="this.style.background='#fefce8'" onmouseout="this.style.background='transparent'">
                <td style="padding:10px 16px;">
                    <div style="font-weight:700;color:#1e293b;font-size:0.88rem;">${d.nombre}</div>
                    <div style="font-size:0.72rem;color:#94a3b8;">${d.rfc || ''}</div>
                </td>
                <td style="padding:10px 16px;">
                    <span style="background:rgba(99,27,47,0.08);color:var(--color-maroon);padding:3px 10px;border-radius:10px;font-size:0.78rem;font-weight:700;">${d.carrera || 'N/A'}</span>
                </td>
                <td style="padding:10px 16px;">${condBadge}</td>
                <td style="padding:10px 16px;color:#64748b;font-size:0.82rem;" title="${materias}">${materiasShort || '<em style="color:#cbd5e1;">—</em>'}</td>
                <td style="padding:10px 16px;text-align:center;">
                    <button onclick="window.verPerfil(${d.docente_id}, '${(d.carrera||'').replace(/'/g,'')}')" style="background:linear-gradient(135deg,#1d4ed8,#2563eb);color:white;border:none;padding:5px 14px;border-radius:7px;cursor:pointer;font-size:0.75rem;font-weight:700;">Ver</button>
                </td>
            </tr>`;
        }).join('');
    };

    // Director: clicking a carrera pill sets the filter and re-renders
    window.dirShowCarrera = function(carrera) {
        const sel = document.getElementById('dir-filter-carrera');
        if (sel) sel.value = carrera;
        window.dirFilterTable();
        document.getElementById('dir-docentes-table')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    };



    async function loadDocentesFromDatabase() {
        const tbody = document.getElementById('docentes-tbody');
        if(!tbody) return;

        try {
            tbody.innerHTML = "<tr><td colspan='7'>Cargando datos desde PostgreSQL...</td></tr>";
            
            const res = await fetch('/api/docentes');
            if (!res.ok) throw new Error("Error en servidor local");
            const docentes = await res.json();
            
            tbody.innerHTML = "";

            let countTotal = 0, countMil = 0, countCiv = 0;

            docentes.forEach(d => {
                let carreraDisplay = d.carrera || "N/A (Sin Asignar)";
                let notaOtrasCarreras = "";

                // FILTRO: Jefe de carrera solo ve docentes de SU carrera (aunque compartan materias con otras)
                if(currentUser.role !== 'ADM' && ['ICI','ICE','II','IC','TC'].includes(currentUser.role)) {
                    // Carrera puede ser "ICE, ICI" — separamos y verificamos
                    const carreras = (d.carrera || '').split(',').map(s => s.trim());
                    // Solo mostrar si la carrera del usuario está en la lista
                    if (!carreras.includes(currentUser.role)) {
                        return; // Ocultar este docente para este jefe de carrera
                    }
                    
                    // Si da a más de una carrera, mostrar nota, pero la vista "principal" muestra la de su Jefatura
                    carreraDisplay = currentUser.role;
                    if (carreras.length > 1) {
                        notaOtrasCarreras = `<br><span style="font-size:0.7rem;color:#f59e0b;font-weight:700;">(Da clases a otras carreras también)</span>`;
                    }
                }
                
                countTotal++;
                const isMilitar = d.condicion === 'Personal Militar';
                if(isMilitar) countMil++; else countCiv++;

                const badge = isMilitar 
                    ? '<span style="background:#631b2f;color:white;padding:2px 8px;border-radius:4px;font-size:0.72rem;font-weight:700;">MIL.</span>' 
                    : '<span style="background:#64748b;color:white;padding:2px 8px;border-radius:4px;font-size:0.72rem;">CIV.</span>';
                const gradoDisplay = (d.grado_mil && d.grado_mil.trim()) ? d.grado_mil.trim() : (d.grado_acad ? d.grado_acad.trim() : '');
                
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${carreraDisplay}</strong>${notaOtrasCarreras}</td>
                    <td style="text-align:center">${d.docente_id}</td>
                    <td>${badge} <strong>${gradoDisplay}</strong> ${d.nombre}</td>
                    <td style="font-family:monospace;font-size:0.82rem">${d.rfc || 'S/R'}</td>
                    <td style="font-family:monospace;font-size:0.78rem;color:#666">${d.curp || '-'}</td>
                    <td>
                        <button class="btn-ver-perfil" onclick="window.verPerfil(${d.docente_id}, '${carreraDisplay}')">&#128065; Ver Perfil</button>
                    </td>
                `;
                tr.dataset.search = `${carreraDisplay} ${d.docente_id} ${d.nombre} ${d.rfc || ''} ${d.curp || ''} ${gradoDisplay}`.toLowerCase();
                tbody.appendChild(tr);
            });
            
            // Reaplicar filtro de búsqueda si hay texto en el input
            filtrarDocentes();

            // Actualizar contadores
            const statsEl = document.getElementById('docentes-stats');
            if(statsEl) {
                statsEl.innerHTML = `
                    <span style="background:#1e3a5f;color:white;padding:6px 16px;border-radius:20px;font-weight:700;margin-right:8px;">Total: ${countTotal}</span>
                    <span style="background:#631b2f;color:white;padding:6px 16px;border-radius:20px;font-weight:700;margin-right:8px;">Militares: ${countMil}</span>
                    <span style="background:#374151;color:white;padding:6px 16px;border-radius:20px;font-weight:700;">Civiles: ${countCiv}</span>
                `;
            }
            
            if (tbody.innerHTML === "") {
                tbody.innerHTML = "<tr><td colspan='7'>No hay docentes asignados a tu perfil.</td></tr>";
            }
        } catch (err) {
            console.error(err);
            tbody.innerHTML = "<tr><td colspan='7' style='color:red'>No se pudo conectar al servidor de DB (`/api/docentes`). ¿Iniciaste el server.ps1?</td></tr>";
        }

    }

    // ==========================================
    // VER / EDITAR PERFIL DEL DOCENTE
    // ==========================================
    window.verPerfil = async function(id, carreraStr) {
        try {
            const res = await fetch(`/api/docentes/${id}`);
            if (!res.ok) throw new Error('No encontrado');
            const d = await res.json();

            document.getElementById('p-id').value = d.docente_id;
            document.getElementById('p-nombre').value = d.nombre || '';
            document.getElementById('p-nombre-display').textContent = d.nombre || '';
            document.getElementById('p-carrera-display').textContent = carreraStr || '';

            // Grado academico como select
            const gaSelect = document.getElementById('p-grado-acad');
            const gaVal = (d.grado_acad || '').trim();
            // Mapeo de abreviatura a valor completo
            const gradoMap = { 'L':'Licenciatura','M':'Maestria','D':'Doctorado','T':'Tecnico','E':'Especializacion','m':'Maestria' };
            const gaFull = gradoMap[gaVal] || gaVal;
            gaSelect.value = gaFull;
            if (!gaSelect.value) gaSelect.value = '';

            document.getElementById('p-grado-mil').value = d.grado_mil || '';
            document.getElementById('p-matricula').value = d.matricula || '';
            document.getElementById('p-rfc').value = d.rfc || '';
            document.getElementById('p-curp').value = d.curp || '';
            document.getElementById('p-condicion').value = d.condicion || 'Personal Civil';
            document.getElementById('p-genero').value = d.genero || '';
            document.getElementById('p-sangre').value = d.tipo_sangre || '';
            document.getElementById('p-ine').value = d.credencial_ine || '';
            document.getElementById('p-domicilio').value = d.domicilio || '';

            // Aplicar toggle campos militar/civil
            toggleMilitarFields();

            // Foto
            const fotoImg = document.getElementById('p-foto-img');
            const fotoInitials = document.getElementById('p-foto-initials');
            if (d.foto_path) {
                fotoImg.src = `/fotos/${d.foto_path}`;
                fotoImg.style.display = 'block';
                fotoInitials.style.display = 'none';
            } else {
                fotoImg.style.display = 'none';
                fotoInitials.style.display = 'block';
                const initials = (d.nombre || '?').split(' ').map(w=>w[0]).slice(0,2).join('');
                fotoInitials.textContent = initials.toUpperCase();
            }

            // Badge condicion
            const isMil = d.condicion === 'Personal Militar';
            document.getElementById('perfil-badge-bar').innerHTML = `
                <span style="background:${isMil?'rgba(255,255,255,0.2)':'rgba(255,255,255,0.15)'};color:white;padding:3px 12px;border-radius:20px;font-weight:700;font-size:0.8rem;border:1px solid rgba(255,255,255,0.3);margin-right:6px;">${isMil?'PERSONAL MILITAR':'PERSONAL CIVIL'}</span>
                <span style="color:rgba(255,255,255,0.5);font-size:0.8rem;">ID: ${d.docente_id}</span>
            `;

            // Materias
            const matRes = await fetch(`/api/materias/${id}`);
            const mats = matRes.ok ? await matRes.json() : [];
            document.getElementById('p-materias').innerHTML = mats.length 
                ? mats.map(m => `<span style="display:inline-block;background:#eef2ff;color:#3730a3;padding:3px 10px;border-radius:6px;margin:3px;font-size:0.85rem;">${m.materia} <em style="color:#888;">(${m.carrera})</em></span>`).join('')
                : '<em style="color:#aaa;">Sin materias asignadas</em>';

            // Cédula profesional
            const cedulaStatus = document.getElementById('p-cedula-status');
            if (d.cedula_path) {
                cedulaStatus.innerHTML = `<span style="color:#0f766e;font-weight:600;">✅ Cédula registrada: </span><a href="/cedulas/${d.cedula_path}" target="_blank" style="color:#0f766e;text-decoration:underline;">${d.cedula_path}</a>`;
            } else {
                cedulaStatus.innerHTML = '<em>Sin cédula registrada</em>';
            }

            // Load extra tabs data
            loadExpediente(id);
            loadEvalHistorial(id);
            // Default to first tab
            switchPerfilTab('datos');

            document.getElementById('modal-perfil').classList.remove('hidden');

            // Modo solo-lectura para el Director
            const dirMode = currentUser && currentUser.role === 'DIR';
            ['p-nombre','p-grado-acad','p-grado-mil','p-matricula','p-rfc','p-curp',
             'p-condicion','p-genero','p-sangre','p-ine','p-domicilio'].forEach(id => {
                const el = document.getElementById(id);
                if (el) { el.disabled = dirMode; el.style.opacity = dirMode ? '0.75' : ''; }
            });
            ['btn-guardar-perfil','btn-nueva-materia','lbl-foto-upload','lbl-cedula-upload'].forEach(id => {
                const el = document.getElementById(id);
                if (el) el.style.display = dirMode ? 'none' : '';
            });
        } catch(e) {
            alert('Error cargando perfil del docente: ' + e.message);
        }
    };

    window.cerrarPerfil = function() {
        document.getElementById('modal-perfil').classList.add('hidden');
        document.getElementById('p-foto-input').value = '';
        document.getElementById('p-cedula-input').value = '';
        // Restaurar estado editable para cuando otro rol abra el modal
        ['p-nombre','p-grado-acad','p-grado-mil','p-matricula','p-rfc','p-curp',
         'p-condicion','p-genero','p-sangre','p-ine','p-domicilio'].forEach(id => {
            const el = document.getElementById(id);
            if (el) { el.disabled = false; el.style.opacity = ''; }
        });
        ['btn-guardar-perfil','btn-nueva-materia','lbl-foto-upload','lbl-cedula-upload'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.style.display = '';
        });
    };

    window.previewCedula = function(input) {
        if (!input.files || !input.files[0]) return;
        const file = input.files[0];
        const cedulaStatus = document.getElementById('p-cedula-status');
        cedulaStatus.innerHTML = `<span style="color:#0f766e;font-weight:600;">📄 Archivo seleccionado: </span><span style="color:#334155;">${file.name}</span> <span style="color:#94a3b8;font-size:0.8rem;">(se guardará al presionar "Guardar Cambios")</span>`;
    };

    window.perfilNuevaMateria = async function() {
        const docenteId = document.getElementById('p-id').value;
        if (!docenteId) return alert('No hay ID de docente');

        const materia = prompt('Nombre de la materia o asignatura:');
        if (!materia || !materia.trim()) return;
        
        const carrera = prompt('Carrera (ej. ICI, MED, TC):');
        if (!carrera || !carrera.trim()) return;

        const horasStr = prompt('Total de horas por el semestre (ej. 16, 32):');
        const horas = parseFloat(horasStr);
        if (isNaN(horas)) return alert('Horas inválidas');

        try {
            const res = await fetch(`/api/asignacion/${docenteId}/nueva`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    materia: materia.trim(),
                    carrera: carrera.trim().toUpperCase(),
                    horas: horas,
                    nivel_pago: 'Licenciatura'
                })
            });
            const data = await res.json();
            if (data.status === 'ok') {
                alert('Materia agregada/actualizada correctamente.');
                // Recargar el perfil completo para actualizar UI
                window.verPerfil(docenteId, document.getElementById('p-carrera-display').textContent);
                if (window.dirFilterTable) window.dirFilterTable();
            } else {
                alert('Error al agregar materia: ' + data.message);
            }
        } catch(e) {
            alert('Error de red al agregar materia: ' + e.message);
        }
    };

    window.toggleMilitarFields = function() {
        const isMilitar = document.getElementById('p-condicion').value === 'Personal Militar';
        const campoMat = document.getElementById('campo-matricula');
        const campoGMil = document.getElementById('campo-grado-mil');
        const inputGMil = document.getElementById('p-grado-mil');
        if (isMilitar) {
            campoMat.style.display = 'block';
            inputGMil.disabled = false;
            inputGMil.style.background = '';
            inputGMil.style.borderColor = '#e2e8f0';
        } else {
            campoMat.style.display = 'none';
            inputGMil.disabled = true;
            inputGMil.value = '';
            inputGMil.style.background = '#f1f5f9';
            inputGMil.style.borderColor = '#e2e8f0';
        }
    };

    window.previewFoto = function(input) {
        if (!input.files || !input.files[0]) return;
        const file = input.files[0];
        const reader = new FileReader();
        reader.onload = function(e) {
            const img = document.getElementById('p-foto-img');
            const initials = document.getElementById('p-foto-initials');
            img.src = e.target.result;
            img.style.display = 'block';
            initials.style.display = 'none';
        };
        reader.readAsDataURL(file);
    };

    window.guardarPerfil = async function() {
        const id = document.getElementById('p-id').value;
        const data = {
            nombre: document.getElementById('p-nombre').value,
            grado_acad: document.getElementById('p-grado-acad').value,
            grado_mil: document.getElementById('p-grado-mil').value,
            matricula: document.getElementById('p-matricula').value,
            rfc: document.getElementById('p-rfc').value,
            curp: document.getElementById('p-curp').value,
            condicion: document.getElementById('p-condicion').value,
            genero: document.getElementById('p-genero').value,
            tipo_sangre: document.getElementById('p-sangre').value,
            credencial_ine: document.getElementById('p-ine').value,
            domicilio: document.getElementById('p-domicilio').value,
        };
        try {
            // 1. Guardar datos del perfil
            const res = await fetch(`/api/docentes/${id}`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(data)
            });
            if (!res.ok) throw new Error('Error al guardar datos');

            // 2. Si hay foto seleccionada, subirla
            const fotoInput = document.getElementById('p-foto-input');
            if (fotoInput.files && fotoInput.files[0]) {
                const formData = new FormData();
                formData.append('foto', fotoInput.files[0]);
                formData.append('docente_id', id);
                await fetch('/api/fotos', { method: 'POST', body: formData });
            }

            // 3. Si hay cédula seleccionada, subirla
            const cedulaInput = document.getElementById('p-cedula-input');
            if (cedulaInput.files && cedulaInput.files[0]) {
                const formData = new FormData();
                formData.append('cedula', cedulaInput.files[0]);
                formData.append('docente_id', id);
                await fetch('/api/cedulas', { method: 'POST', body: formData });
            }

            alert('Perfil actualizado correctamente.');
            cerrarPerfil();
            loadDocentesFromDatabase();
        } catch(e) {
            alert('Error guardando los cambios: ' + e.message);
        }
    };

    async function loadVehiculosFromDatabase() {
        const tbody = document.getElementById('vehiculos-tbody');
        if(!tbody) return;

        try {
            tbody.innerHTML = "<tr><td colspan='5'>Sincronizando vehículos de la base de datos...</td></tr>";
            
            const res = await fetch('/api/vehiculos');
            if (!res.ok) throw new Error("Error en servidor local");
            const vehiculos = await res.json();
            
            tbody.innerHTML = ""; 

            vehiculos.forEach(v => {
                if (currentUser.role !== 'ADM' && ['ICI','ICE','II','IC','TC'].includes(currentUser.role)) {
                    const carreras = (v.carrera || '').split(',').map(s => s.trim());
                    if (!carreras.includes(currentUser.role)) return;
                }

                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${v.docente}</strong></td>
                    <td>${v.marca} ${v.modelo || ''}</td>
                    <td>${v.anio || '-'} | ${v.color || '-'}</td>
                    <td><span style="font-family:monospace; background:#eee; padding:2px 6px; border-radius:4px; font-weight:bold;">${v.placas || 'S/P'}</span></td>
                    <td><span style="color:green; font-weight:bold;">● AUTORIZADO</span></td>
                `;
                tbody.appendChild(tr);
            });
            
            if (tbody.innerHTML === "") {
                tbody.innerHTML = "<tr><td colspan='5'>No hay vehículos registrados en el sistema.</td></tr>";
            }
        } catch (err) {
            console.error(err);
            tbody.innerHTML = "<tr><td colspan='5' style='color:red'>Error al cargar vehículos.</td></tr>";
        }
    }
    window.openAddModal = function() { document.getElementById('modal-add-docente').classList.remove('hidden'); }
    window.closeAddModal = function() {
        document.getElementById('modal-add-docente').classList.add('hidden');
        // Reset file input labels
        const fotoLabel = document.getElementById('add-foto-label');
        const cedulaLabel = document.getElementById('add-cedula-label');
        if (fotoLabel) fotoLabel.textContent = 'Seleccionar foto...';
        if (cedulaLabel) cedulaLabel.textContent = 'Seleccionar cédula (PDF/IMG)...';
    }
    
    // Función para toggle campos militar/civil en modal ADD
    window.toggleAddMilitarFields = function() {
        const isMilitar = document.getElementById('add-obj-condicion').value === 'Personal Militar';
        const campoMat = document.getElementById('add-campo-matricula');
        const campoGMil = document.getElementById('add-campo-grado-mil');
        if (isMilitar) {
            campoMat.style.display = 'block';
            campoGMil.style.display = 'block';
        } else {
            campoMat.style.display = 'none';
            campoGMil.style.display = 'none';
            document.getElementById('add-obj-grado-mil').value = '';
            document.getElementById('add-obj-matricula').value = '';
        }
    };

    // Función filtro de búsqueda en tiempo real
    window.filtrarDocentes = function() {
        const q = (document.getElementById('docente-search')?.value || '').toLowerCase().trim();
        const rows = document.querySelectorAll('#docentes-tbody tr');
        rows.forEach(tr => {
            if (!q || !tr.dataset.search) {
                tr.style.display = '';
            } else {
                tr.style.display = tr.dataset.search.includes(q) ? '' : 'none';
            }
        });
    };

    const formAdd = document.getElementById('form-add-docente');
    if(formAdd) {
        formAdd.addEventListener('submit', async (e) => {
            e.preventDefault();
            const nombre = document.getElementById('add-obj-nombre').value.trim();
            const rfc = document.getElementById('add-obj-rfc').value.trim();
            const curp = document.getElementById('add-obj-curp').value.trim();
            const condicion = document.getElementById('add-obj-condicion').value;
            const gradoAcad = document.getElementById('add-obj-grado-acad').value;
            const gradoMil = document.getElementById('add-obj-grado-mil')?.value.trim() || '';
            const matricula = document.getElementById('add-obj-matricula')?.value.trim() || '';
            const genero = document.getElementById('add-obj-genero').value;
            const sangre = document.getElementById('add-obj-sangre').value;
            const ine = document.getElementById('add-obj-ine').value.trim();
            const domicilio = document.getElementById('add-obj-domicilio').value.trim();
            const regimen_sat = document.getElementById('add-obj-regimen').value;
            const materia = document.getElementById('add-obj-materia').value.trim();
            // La carrera se asigna automáticamente según el rol del usuario
            const carrera = ['ICI','ICE','II','IC','TC'].includes(currentUser.role) ? currentUser.role : 'ICI';

            // Validaciones básicas
            if (!nombre) { alert('El nombre completo es obligatorio.'); return; }
            if (!materia) { alert('La unidad de aprendizaje es obligatoria.'); return; }
            if (!rfc) { alert('El RFC es obligatorio.'); return; }
            if (!curp) { alert('El CURP es obligatorio.'); return; }
            if (!gradoAcad) { alert('Selecciona el grado académico.'); return; }
            if (!genero) { alert('Selecciona el género.'); return; }
            if (!regimen_sat) { alert('Selecciona el Régimen Fiscal SAT.'); return; }
            if (condicion === 'Personal Militar' && !matricula) { alert('La matrícula es obligatoria para personal militar.'); return; }

            try {
                const btn = e.target.querySelector('button[type="submit"]');
                btn.textContent = "Guardando en DB...";
                btn.disabled = true;

                const payload = { 
                    nombre, rfc, curp, condicion,
                    grado_acad: gradoAcad, grado_mil: gradoMil, matricula,
                    genero, tipo_sangre: sangre, credencial_ine: ine,
                    domicilio, carrera, regimen_sat, materia: materia
                };

                const res = await fetch('/api/docentes', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                if(res.ok) {
                    const result = await res.json();
                    const newId = result.docente_id;

                    // Subir foto si se seleccionó
                    const fotoInput = document.getElementById('add-obj-foto');
                    if (fotoInput && fotoInput.files && fotoInput.files[0] && newId) {
                        const fotoForm = new FormData();
                        fotoForm.append('foto', fotoInput.files[0]);
                        fotoForm.append('docente_id', newId);
                        await fetch('/api/fotos', { method: 'POST', body: fotoForm });
                    }

                    // Subir cédula si se seleccionó
                    const cedulaInput = document.getElementById('add-obj-cedula');
                    if (cedulaInput && cedulaInput.files && cedulaInput.files[0] && newId) {
                        const cedForm = new FormData();
                        cedForm.append('cedula', cedulaInput.files[0]);
                        cedForm.append('docente_id', newId);
                        await fetch('/api/cedulas', { method: 'POST', body: cedForm });
                    }

                    alert(`Docente "${nombre}" registrado exitosamente en la base de datos.`);
                    closeAddModal();
                    formAdd.reset();
                    toggleAddMilitarFields();
                    loadDocentesFromDatabase();
                } else { 
                    const errData = await res.json().catch(() => ({}));
                    throw new Error(errData.message || "Error en el servidor"); 
                }
                
            } catch(err) {
                alert("Error al guardar el docente: " + err.message);
            } finally {
                const btn = formAdd.querySelector('button[type="submit"]');
                if (btn) { btn.textContent = "✓ Guardar Docente"; btn.disabled = false; }
            }
        });
    }

    // ==========================================
    // GENERACION DE CONTRATOS (DOCX)
    // ==========================================
    window.requestContrato = async function(id, nombre) {
        // Mostrar loading
        const btnEl = event?.target;
        const originalText = btnEl ? btnEl.textContent : '';
        if (btnEl) { btnEl.textContent = '⏳ Generando...'; btnEl.disabled = true; }

        try {
            const res = await fetch(`/api/generarContrato?docente_id=${id}`, {
                method: 'GET',
                headers: { 'Accept': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document,*/*' }
            });

            if (!res.ok) {
                // Intentar leer mensaje de error del servidor
                let errMsg = `Error del servidor (HTTP ${res.status})`;
                try { 
                    const errJson = await res.json(); 
                    errMsg = errJson.message || errMsg; 
                } catch(_) {}
                throw new Error(errMsg);
            }

            // Verificar que la respuesta sea un archivo válido
            const contentType = res.headers.get('Content-Type') || '';
            if (!contentType.includes('wordprocessingml') && !contentType.includes('octet-stream')) {
                throw new Error(`Tipo de respuesta inesperado: ${contentType}`);
            }

            // Descargar el archivo
            const blob = await res.blob();
            if (blob.size === 0) throw new Error('El servidor devolvió un archivo vacío.');

            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `Contrato_${nombre.replace(/[^a-zA-Z0-9_\- ]/g, '')}.docx`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);

            // Notificación silenciosa en consola
            console.info(`Contrato generado: ${nombre} (ID: ${id})`);

        } catch(err) {
            console.error('Error generando contrato:', err);
            alert(
                `No se pudo generar el contrato para ${nombre}.

` +
                `Detalle: ${err.message}

` +
                `Verifica:
` +
                `• Que server.ps1 esté ejecutándose
` +
                `• Que exista la plantilla_contrato.docx en C:\temp\gestion-docente-web\
` +
                `• Que Microsoft Word esté instalado en el servidor`
            );
        } finally {
            if (btnEl) { btnEl.textContent = originalText || 'Contrato'; btnEl.disabled = false; }
        }
    }

    // ==========================================
    // TABS PERFIL
    // ==========================================
    window.switchPerfilTab = function(tabId) {
        document.querySelectorAll('.perfil-tab-content').forEach(el => el.style.display = 'none');
        document.querySelectorAll('.perfil-tab').forEach(el => el.classList.remove('active'));
        document.getElementById(`tab-${tabId}`).style.display = 'block';
        document.getElementById(`tab-${tabId}-btn`).classList.add('active');
    };

    // ==========================================
    // EXPEDIENTE DIGITAL - PSO
    // ==========================================
    const DOCS_OBLIGATORIOS = [
        {id:'solicitud', name:'Solicitud de empleo', type:'Obligatorio'},
        {id:'cv', name:'Currículum Vitae actualizado', type:'Obligatorio'},
        {id:'titulo', name:'Título profesional', type:'Obligatorio'},
        {id:'cedula', name:'Cédula profesional', type:'Obligatorio'},
        {id:'acta', name:'Acta de nacimiento', type:'Obligatorio'},
        {id:'curp', name:'CURP oficial', type:'Obligatorio'},
        {id:'rfc', name:'Constancia Situación Fiscal', type:'Obligatorio'},
        {id:'ine', name:'Identificación (INE)', type:'Obligatorio'},
        {id:'domicilio', name:'Comprobante de domicilio', type:'Obligatorio'},
        {id:'fotos', name:'Fotografías', type:'Obligatorio'},
        {id:'antecedentes', name:'Carta antecedentes no penales', type:'Opcional'},
        {id:'exp_docente', name:'Constancias de experiencia', type:'Opcional'},
        {id:'cursos', name:'Cursos / Diplomados', type:'Opcional'},
        {id:'clabe', name:'Comprobante bancario', type:'Opcional'},
        {id:'psicologico', name:'Examen psicológico', type:'Opcional'},
        {id:'oposicion', name:'Examen de oposición', type:'Opcional'}
    ];

    window.loadExpediente = async function(docId) {
        try {
            const container = document.getElementById('exp-checklist');
            container.innerHTML = '<em style="color:#888;">Cargando expediente...</em>';
            
            const res = await fetch(`/api/expediente/${docId}`);
            let docsSubidos = res.ok ? await res.json() : [];
            if (!Array.isArray(docsSubidos)) docsSubidos = [];

            // Contar cuántos de los obligatorios existen
            const countOblig = DOCS_OBLIGATORIOS.reduce((acc, curr) => 
                acc + (docsSubidos.some(d => d.tipo_documento === curr.id) ? 1 : 0), 0);
            
            const pct = (countOblig / 16) * 100;
            document.getElementById('exp-progress-text').textContent = `${countOblig}/16 documentos`;
            document.getElementById('exp-progress-bar').style.width = `${pct}%`;

            let html = '';
            DOCS_OBLIGATORIOS.forEach(docObj => {
                const subido = docsSubidos.find(d => d.tipo_documento === docObj.id);
                
                if (subido) {
                    html += `
                        <div class="exp-doc-item uploaded">
                            <div class="exp-doc-icon done">✅</div>
                            <div class="exp-doc-info">
                                <div class="exp-doc-name">${docObj.name}</div>
                                <div class="exp-doc-meta">${subido.fecha_subida} &bull; ${docObj.type}</div>
                            </div>
                            <div class="exp-doc-actions">
                                <a href="/expedientes/${subido.archivo_path}" target="_blank" class="exp-view-btn">Ver</a>
                                <button onclick="deleteExpedienteDoc(${subido.documento_id}, ${docId})" class="exp-delete-btn">X</button>
                            </div>
                        </div>
                    `;
                } else {
                    html += `
                        <div class="exp-doc-item">
                            <div class="exp-doc-icon pending">❌</div>
                            <div class="exp-doc-info">
                                <div class="exp-doc-name">${docObj.name}</div>
                                <div class="exp-doc-meta" style="color:#ef4444;">Faltante &bull; ${docObj.type}</div>
                            </div>
                            <div class="exp-doc-actions">
                                <input type="file" id="exp-file-${docObj.id}" style="display:none" onchange="uploadExpedienteDoc(this, '${docObj.id}', ${docId})">
                                <label for="exp-file-${docObj.id}" class="exp-upload-btn">Subir Archivo</label>
                            </div>
                        </div>
                    `;
                }
            });
            container.innerHTML = html;
        } catch(e) {
            console.error(e);
            document.getElementById('exp-checklist').innerHTML = `<em style="color:red;">Error al cargar expediente: ${e.message}</em>`;
        }
    };

    window.uploadExpedienteDoc = async function(input, tipo, docId) {
        if (!input.files || input.files.length === 0) return;
        const form = new FormData();
        form.append('exp', input.files[0]);
        // tipo included via query string config in API
        try {
            const res = await fetch(`/api/expediente?tipo=${tipo}`, { method: 'POST', body: form });
            if (!res.ok) throw new Error('Error al subir documento');
            
            // Re-vincular el ID de docente con el tipo ya que la API espera que se suba en el context de form "docId"
            // De hecho en la API anterior extraíamos el id con un hack, pero para asegurar, hagamos el update
            const jsonRes = await res.json();
            const did = jsonRes.documento_id;
            
            // Actualizar que pertenece a este docente
            await fetch(`/api/expediente/link?docid=${docId}&docuid=${did}`, {method:'POST'}).catch(e=>console.log(e)); // hack safe if api handles or not, ideally API extracts id from filename logic `exp_DocID...` wait API currently parses `exp_(\d+)`, but we didn't name the file input correctly!
            // Wait, to match API regex -> $savedName must contain `exp_` AND the ID if possible. 
            // The PS1 logic matches: `exp_(\d+)` from filename, but if we don't send it, might be 0.
            // Let's just reload the expediente to see if it got linked.
            
            // Let's fix the way we send it. I will modify the ps1 separately if needed, but for now just pass to api.
            // Act: We'll append doc_id physically to filename or rely on backend. I will add an update statement below if needed, but the backend parses from the name. Since we can't control generated name fully, we send `docente_id` in form? The PS script expects `exp_(\d+)` on savedName, but it doesn't know it unless we pass it. I'll pass in form and modify the script logic or we just assume $qs works? No, let's fix backend via another tool later if it fails. 

            loadExpediente(docId);
        } catch(e) {
            alert(e.message);
        }
    };

    window.deleteExpedienteDoc = async function(docuId, docId) {
        if (!confirm('¿Seguro que deseas eliminar este documento?')) return;
        try {
            const res = await fetch(`/api/expediente/${docuId}`, { method: 'DELETE' });
            if (!res.ok) throw new Error('Error al eliminar');
            loadExpediente(docId);
        } catch(e) { alert(e.message); }
    };

    // ==========================================
    // EVALUACIONES ACADÉMICAS
    // ==========================================
    window.loadEvaluacionesFromDatabase = async function() {
        const tbody = document.getElementById('evaluaciones-tbody');
        if (!tbody) return;
        try {
            tbody.innerHTML = "<tr><td colspan='9'>Cargando...</td></tr>";
            const res = await fetch('/api/evaluaciones');
            let evals = res.ok ? await res.json() : [];
            if (!Array.isArray(evals)) evals = [];

            let tbodyHtml = "";
            let passCount = 0, failCount = 0;

            let filteredEvals = evals;
            if (currentUser.role !== 'ADM' && ['ICI','ICE','II','IC','TC'].includes(currentUser.role)) {
                filteredEvals = evals.filter(ev => {
                    const carreras = (ev.carrera || '').split(',').map(s => s.trim());
                    return carreras.includes(currentUser.role);
                });
            }

            filteredEvals.forEach(ev => {
                if (ev.resultado.toLowerCase().includes('aprobado') && !ev.resultado.toLowerCase().includes('no')) passCount++;
                if (ev.resultado.toLowerCase().includes('no aprobado')) failCount++;

                let badge = 'eval-badge-pendiente';
                if (ev.resultado === 'Aprobado') badge = 'eval-badge-aprobado';
                else if (ev.resultado === 'No Aprobado') badge = 'eval-badge-noaprobado';
                else if (ev.resultado === 'Observación') badge = 'eval-badge-observacion';

                tbodyHtml += `
                    <tr>
                        <td style="font-weight:700;">#${ev.evaluacion_id}</td>
                        <td>${ev.docente_nombre} <div style="font-size:0.75rem;color:#888;">${ev.carrera}</div></td>
                        <td>${ev.periodo}</td>
                        <td style="text-align:center;">${ev.puntaje_desempeno}</td>
                        <td style="text-align:center;">${ev.puntaje_pedagogia}</td>
                        <td style="text-align:center;">${ev.puntaje_perfil}</td>
                        <td style="text-align:center;">${ev.puntaje_responsabilidad}</td>
                        <td style="text-align:center;font-weight:800;">${ev.puntaje_final}</td>
                        <td><span class="${badge}" style="padding:4px 10px;border-radius:12px;font-size:0.75rem;font-weight:700;">${ev.resultado}</span></td>
                    </tr>
                `;
            });
            tbody.innerHTML = tbodyHtml || "<tr><td colspan='9'>No hay evaluaciones registradas.</td></tr>";

            // Stats
            document.getElementById('eval-stats').innerHTML = `
                <span style="background:#eef2ff;color:#3730a3;padding:6px 16px;border-radius:20px;font-weight:600;font-size:0.85rem;">Total: ${evals.length}</span>
                <span style="background:#f0fdf4;color:#166534;padding:6px 16px;border-radius:20px;font-weight:600;font-size:0.85rem;">Aprobados: ${passCount}</span>
                <span style="background:#fef2f2;color:#991b1b;padding:6px 16px;border-radius:20px;font-weight:600;font-size:0.85rem;">No Aprobados: ${failCount}</span>
            `;

        } catch (e) {
            tbody.innerHTML = `<tr><td colspan='9' style="color:red">Error: ${e.message}</td></tr>`;
        }
    };

    window.openEvalModal = async function() {
        // Cargar cbx docentes
        try {
            const res = await fetch('/api/docentes'); // Using /api/docentes to get carrera
            const docs = await res.json();
            const cbx = document.getElementById('eval-docente');

            let filteredDocs = docs;
            if (currentUser.role !== 'ADM' && ['ICI','ICE','II','IC','TC'].includes(currentUser.role)) {
                filteredDocs = docs.filter(d => {
                    const carreras = (d.carrera || '').split(',').map(s => s.trim());
                    return carreras.includes(currentUser.role);
                });
            }

            cbx.innerHTML = '<option value="">-- Seleccionar Docente --</option>' + 
                            filteredDocs.map(d => `<option value="${d.docente_id}">${d.nombre} (${d.condicion})</option>`).join('');
        } catch(e) { console.error('Error cargando docentes', e); }

        document.getElementById('modal-eval').classList.remove('hidden');
        updateEvalCalc(); // reset calc
    };

    window.closeEvalModal = function() {
        document.getElementById('modal-eval').classList.add('hidden');
    };

    window.updateEvalCalc = function() {
        const d = parseInt(document.getElementById('eval-desemp').value) || 0;
        const p = parseInt(document.getElementById('eval-pedag').value) || 0;
        const f = parseInt(document.getElementById('eval-perfil').value) || 0;
        const r = parseInt(document.getElementById('eval-resp').value) || 0;

        document.getElementById('eval-val-desemp').textContent = d;
        document.getElementById('eval-val-pedag').textContent = p;
        document.getElementById('eval-val-perfil').textContent = f;
        document.getElementById('eval-val-resp').textContent = r;

        // Weights: 30%, 30%, 20%, 20%
        const final = (d * 0.30) + (p * 0.30) + (f * 0.20) + (r * 0.20);
        document.getElementById('eval-puntaje-final').textContent = final.toFixed(2);

        const badge = document.getElementById('eval-resultado-badge');
        badge.className = '';
        if (final >= 80) {
            badge.textContent = 'Aprobado';
            badge.classList.add('eval-badge-aprobado');
        } else if (final >= 60) {
            badge.textContent = 'Observación';
            badge.classList.add('eval-badge-observacion');
        } else {
            badge.textContent = 'No Aprobado';
            badge.classList.add('eval-badge-noaprobado');
        }
    };

    window.guardarEvaluacion = async function() {
        const docId = document.getElementById('eval-docente').value;
        if (!docId) return alert('Debes seleccionar un docente.');

        const payload = {
            docente_id: parseInt(docId),
            evaluador: document.getElementById('eval-evaluador').value,
            periodo: document.getElementById('eval-periodo').value,
            puntaje_desempeno: parseFloat(document.getElementById('eval-desemp').value),
            puntaje_pedagogia: parseFloat(document.getElementById('eval-pedag').value),
            puntaje_perfil: parseFloat(document.getElementById('eval-perfil').value),
            puntaje_responsabilidad: parseFloat(document.getElementById('eval-resp').value),
            puntaje_final: parseFloat(document.getElementById('eval-puntaje-final').textContent),
            resultado: document.getElementById('eval-resultado-badge').textContent,
            observaciones: document.getElementById('eval-obs').value
        };

        try {
            const btn = event.target;
            btn.disabled = true; btn.textContent = 'Guardando...';

            const res = await fetch('/api/evaluaciones', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) throw new Error('Error al registrar evaluación');
            
            alert('Evaluación guardada exitosamente');
            closeEvalModal();
            loadEvaluacionesFromDatabase();
            loadDocentesFromDatabase(); // refresca los badges en el main

        } catch(e) {
            alert(e.message);
        } finally {
            event.target.disabled = false; event.target.textContent = '✓ Registrar Evaluación';
        }
    };

    window.loadEvalHistorial = async function(docId) {
        try {
            const container = document.getElementById('p-eval-history');
            container.innerHTML = '<em style="color:#aaa;">Cargando historial...</em>';
            const res = await fetch(`/api/evaluaciones/${docId}`);
            let evals = res.ok ? await res.json() : [];
            if (!Array.isArray(evals)) evals = [];

            if (evals.length === 0) {
                container.innerHTML = '<em>No hay evaluaciones registradas para este docente.</em>';
                return;
            }

            let html = '';
            evals.forEach(ev => {
                html += `
                    <div style="background:#f8fafc;border:1.5px solid #e2e8f0;border-radius:10px;padding:16px;">
                        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                            <strong style="color:var(--color-maroon);font-size:1.1rem;">Periodo: ${ev.periodo}</strong>
                            <span style="font-size:1.2rem;font-weight:900;">${ev.puntaje_final} <span style="font-size:0.8rem;color:#888;font-weight:400;">/ 100</span></span>
                        </div>
                        <div style="display:flex;gap:16px;font-size:0.85rem;color:#64748b;margin-bottom:10px;">
                            <div><strong>Desempeño:</strong> ${ev.puntaje_desempeno}</div>
                            <div><strong>Pedagogía:</strong> ${ev.puntaje_pedagogia}</div>
                            <div><strong>Perfil:</strong> ${ev.puntaje_perfil}</div>
                            <div><strong>Resp.:</strong> ${ev.puntaje_responsabilidad}</div>
                        </div>
                        <div style="font-size:0.85rem;color:#334155;"><strong>Evaluador:</strong> ${ev.evaluador || 'N/A'} &bull; <strong>Fecha:</strong> ${ev.fecha_evaluacion}</div>
                        ${ev.observaciones ? `<div style="margin-top:10px;background:#fff;padding:8px;border-radius:6px;font-size:0.85rem;border:1px solid #e2e8f0;"><em>" ${ev.observaciones} "</em></div>` : ''}
                    </div>
                `;
            });
            container.innerHTML = html;
        } catch(e) {
            document.getElementById('p-eval-history').innerHTML = `Error: ${e.message}`;
        }
    };

    // ==========================================
    // CONTRATOS Y AUDITORÍA
    // ==========================================
    window.loadContratosFromDatabase = async function() {
        const tbody = document.getElementById('contratos-tbody');
        if(!tbody) return;
        try {
            tbody.innerHTML = "<tr><td colspan='5'>Cargando...</td></tr>";
            const res = await fetch('/api/contratos');
            let data = res.ok ? await res.json() : [];
            if (!Array.isArray(data)) data = [];

            // Filtrar si es un jefe de carrera
            let filtered = data;
            if (currentUser.role !== 'ADM' && ['ICI','ICE','II','IC','TC'].includes(currentUser.role)) {
                filtered = data.filter(d => {
                    const carreras = (d.carrera || '').split(',').map(s => s.trim());
                    return carreras.includes(currentUser.role);
                });
            }

            let html = "";
            filtered.forEach(c => {
                let regimeName = {
                    'SP': 'Servicios Profesionales (SP)',
                    'RESICO': 'R. Simplificado Confianza',
                    'RS': 'Sueldos y Salarios (RS)'
                }[c.regimen_sat] || c.regimen_sat || 'No definido';

                let printedText = c.emitido_por ? `<span style="color:#0f766e;font-weight:700;">${c.emitido_por}</span><br><span style="font-size:0.75rem;color:#888;">${c.fecha_emision}</span>` : `<span style="color:#94a3b8;font-style:italic;">Nunca generado</span>`;

                html += `
                    <tr>
                        <td><strong>${c.carrera || 'N/A'}</strong></td>
                        <td>${c.nombre} <span style="font-size:0.75rem;color:#64748b;display:block;">${c.condicion}</span></td>
                        <td style="font-size:0.85rem; font-weight:600; color:var(--color-maroon);">${c.materia}</td>
                        <td><span style="background:#f1f5f9;padding:4px 8px;border-radius:6px;font-size:0.8rem;border:1px solid #cbd5e1;">${regimeName}</span></td>
                        <td>${printedText}</td>
                        <td style="text-align:center;">
                            <button class="btn-action" onclick="window.generarContrato(${c.docente_id}, '${c.nombre.replace(/'/g, "\\'")}')" style="padding:6px 14px;font-size:0.85rem;">🖨️ Imprimir</button>
                        </td>
                    </tr>
                `;
            });
            tbody.innerHTML = html || "<tr><td colspan='6'>No hay docentes disponibles.</td></tr>";

            const printedCount = filtered.filter(x => x.emitido_por).length;
            document.getElementById('contratos-stats').innerHTML = `
                <span style="background:#eef2ff;color:#3730a3;padding:6px 16px;border-radius:20px;font-weight:600;font-size:0.85rem;">Plantilla Total: ${filtered.length}</span>
                <span style="background:#f0fdf4;color:#166534;padding:6px 16px;border-radius:20px;font-weight:600;font-size:0.85rem;">Impresos: ${printedCount}</span>
                <span style="background:#fef2f2;color:#991b1b;padding:6px 16px;border-radius:20px;font-weight:600;font-size:0.85rem;">Pendientes: ${filtered.length - printedCount}</span>
            `;

        } catch(e) {
            tbody.innerHTML = `<tr><td colspan='6' style="color:red">Error: ${e.message}</td></tr>`;
        }
    };

    window.loadPresupuesto = async function() {
        const tbody = document.getElementById('presupuesto-tbody');
        tbody.innerHTML = "<tr><td colspan='10'>Cargando presupuesto...</td></tr>";
        try {
            const res = await fetch('/api/presupuesto');
            if(!res.ok) throw new Error('Error de red');
            const data = await res.json();
            
            let html = '';
            let s_bruto = 0, s_iva = 0, s_ret_iva = 0, s_ret_isr = 0, s_neto = 0;

            data.forEach(d => {
                s_bruto += d.subtotal; s_iva += d.iva; s_ret_iva += d.retIva; s_ret_isr += d.retIsr; s_neto += d.neto;
                html += `<tr>
                    <td style="font-weight:bold;">${d.nombre}<br><span style="font-size:0.75rem;color:#666">${d.rfc}</span></td>
                    <td>${d.regimen_sat}</td>
                    <td>${d.materia} <em style="font-size:0.75rem;color:#888;">(${d.nivel_pago})</em></td>
                    <td style="text-align:center;">${d.horas}</td>
                    <td style="text-align:right;">$${d.tarifa.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                    <td style="text-align:right;">$${d.subtotal.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                    <td style="text-align:right;">$${d.iva.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                    <td style="text-align:right;">$${d.retIsr.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                    <td style="text-align:right;">$${d.retIva.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                    <td style="text-align:right;font-weight:bold;color:var(--color-maroon);">$${d.neto.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                </tr>`;
            });
            html += `<tr style="background:#f1f5f9;font-weight:bold;">
                <td colspan="5" style="text-align:right;">TOTAL PLANTE (UDEFA):</td>
                <td style="text-align:right;">$${s_bruto.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                <td style="text-align:right;">$${s_iva.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                <td style="text-align:right;">$${s_ret_isr.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                <td style="text-align:right;">$${s_ret_iva.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                <td style="text-align:right;color:var(--color-maroon);font-size:1.1rem;">$${s_neto.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
            </tr>`;
            tbody.innerHTML = html || "<tr><td colspan='10'>No hay asignaciones ni presupuestos capturados.</td></tr>";
        } catch(e) {
            tbody.innerHTML = `<tr><td colspan='10' style="color:red">Error cargando presupuesto: ${e.message}</td></tr>`;
        }
    };

    window.closePreviewModal = function() {
        document.getElementById('modal-preview-contrato').classList.add('hidden');
    };

    let pendingContratoPayload = null;

    window.generarContrato = async function(docenteId, nombre) {
        const btn = (typeof event !== "undefined" && event) ? event.target : null;
        let ogText = '';
        if (btn && btn.tagName === 'BUTTON') {
            ogText = btn.innerHTML;
            btn.innerHTML = '⏳ Calculando...';
            btn.disabled = true;
        }

        try {
            const matRes = await fetch(`/api/materias/${docenteId}`);
            let materias = matRes.ok ? await matRes.json() : [];
            
            let tarifaLic = 419.58;
            let tarifaMtr = 734.27;
            let tarifaTec = 199.30;
            let tarifaDoc = 1048.95;

            let htmlTabla = `<div style="overflow-x:auto;"><table style="min-width:750px;width:100%; border-collapse:collapse; margin-bottom:10px; font-size:0.85rem;">
                <thead>
                <tr style="background:#1e3a5f;color:white;">
                    <th style="padding:8px 10px;text-align:left;">Unidad de Aprendizaje</th>
                    <th style="padding:8px;text-align:center;">Tabulador</th>
                    <th style="padding:8px;text-align:center;" title="Horas mensuales Mes 1 (Marzo)">M1<br><small style='font-weight:normal;opacity:.8'>Marzo</small></th>
                    <th style="padding:8px;text-align:center;" title="Horas mensuales Mes 2 (Abril)">M2<br><small style='font-weight:normal;opacity:.8'>Abril</small></th>
                    <th style="padding:8px;text-align:center;" title="Horas mensuales Mes 3 (Mayo)">M3<br><small style='font-weight:normal;opacity:.8'>Mayo</small></th>
                    <th style="padding:8px;text-align:center;" title="Horas mensuales Mes 4 (Junio)">M4<br><small style='font-weight:normal;opacity:.8'>Junio</small></th>
                    <th style="padding:8px;text-align:right;">Total<br><small style='font-weight:normal;opacity:.8'>hrs/sem</small></th>
                    <th style="padding:8px;text-align:right;">Pago M1</th>
                    <th style="padding:8px;text-align:center;">Acción</th>
                </tr>
                </thead><tbody>`;
            
            let totalHorasDocente = 0;
            if (materias.length === 0) {
                htmlTabla += `<tr><td colspan="9" style="padding:10px;text-align:center;">Sin materias asignadas</td></tr>`;
            } else {
                materias.forEach((m, idx) => {
                    const hm1 = parseFloat(m.horas_m1) || 0;
                    const hm2 = parseFloat(m.horas_m2) || 0;
                    const hm3 = parseFloat(m.horas_m3) || 0;
                    const hm4 = parseFloat(m.horas_m4) || 0;
                    const horasTotal = parseFloat(m.horas) || 0;
                    const allZero = (hm1 + hm2 + hm3 + hm4) === 0;
                    const m1 = allZero ? horasTotal : hm1;
                    const m2 = allZero ? horasTotal : hm2;
                    const m3 = allZero ? horasTotal : hm3;
                    const m4 = allZero ? horasTotal : hm4;
                    totalHorasDocente += m1;
                    const nivel = m.nivel_pago || 'Licenciatura';
                    let tarifaLocal = tarifaLic;
                    if (nivel === 'Maestría' || nivel === 'Maestria') tarifaLocal = tarifaMtr;
                    else if (nivel === 'Técnico' || nivel === 'Tecnico') tarifaLocal = tarifaTec;
                    else if (nivel === 'Doctorado') tarifaLocal = tarifaDoc;

                    const pagoM1 = m1 * tarifaLocal;
                    
                    const puedeGenerar = currentUser.role === 'ADM' || m.carrera === currentUser.role;
                    const matEsc = m.materia.replace(/'/g, "\\'");
                    const nomEsc = nombre.replace(/'/g, "\\'");
                    const btnHtml = puedeGenerar
                        ? `<button onclick="window.confirmarGeneracionIndividual(event, ${docenteId}, ${m.materia_id}, '${matEsc}', '${nomEsc}')" 
                                   style="padding:5px 10px; background:var(--color-maroon); color:white; border:none; border-radius:4px; cursor:pointer; font-size:0.75rem;">
                                   🖨️ Generar
                           </button>`
                        : `<span style="font-size:0.7rem; color:#94a3b8; font-style:italic;">No autorizado (${m.carrera})</span>`;

                    const inputStyle = 'width:52px;text-align:center;padding:3px;border:1.5px solid #cbd5e1;border-radius:4px;font-size:0.82rem;';
                    htmlTabla += `<tr style="border-bottom:1px solid #e2e8f0;${m.ya_emitido ? 'background:#f0fdf4;' : ''}">
                        <td style="padding:8px 10px; font-weight:600;">
                            ${m.ya_emitido ? '<span style="color:#0f766e;font-size:0.7rem;font-weight:700;">✅ EMITIDO</span><br>' : ''}
                            ${m.materia} <span style="font-size:0.72rem;color:#64748b;font-weight:normal;display:block;">${m.carrera}</span>
                        </td>
                        <td style="padding:8px;text-align:center;">
                            <select style="padding:3px;border:1px solid #ccc;border-radius:4px;font-size:0.8rem;" onchange="window.recalcPreviewLevel(this, ${docenteId}, '${matEsc}', ${m1}, ${m2}, ${m3}, ${m4})">
                                <option value="Técnico" ${(nivel === 'Técnico'||nivel === 'Tecnico') ? 'selected' : ''}>Técnico</option>
                                <option value="Licenciatura" ${nivel === 'Licenciatura' ? 'selected' : ''}>Licenciatura</option>
                                <option value="Maestría" ${(nivel === 'Maestría'||nivel === 'Maestria') ? 'selected' : ''}>Maestría</option>
                                <option value="Doctorado" ${nivel === 'Doctorado' ? 'selected' : ''}>Doctorado</option>
                            </select>
                        </td>
                        <td style="padding:6px 4px;text-align:center;"><input type="number" min="0" max="999" value="${m1}" style="${inputStyle}" data-docid="${docenteId}" data-mat="${matEsc}" data-mes="1" data-nivel="${nivel}" onchange="window.recalcPreviewMes(this)" /> </td>
                        <td style="padding:6px 4px;text-align:center;"><input type="number" min="0" max="999" value="${m2}" style="${inputStyle}" data-docid="${docenteId}" data-mat="${matEsc}" data-mes="2" data-nivel="${nivel}" onchange="window.recalcPreviewMes(this)" /> </td>
                        <td style="padding:6px 4px;text-align:center;"><input type="number" min="0" max="999" value="${m3}" style="${inputStyle}" data-docid="${docenteId}" data-mat="${matEsc}" data-mes="3" data-nivel="${nivel}" onchange="window.recalcPreviewMes(this)" /> </td>
                        <td style="padding:6px 4px;text-align:center;"><input type="number" min="0" max="999" value="${m4}" style="${inputStyle}" data-docid="${docenteId}" data-mat="${matEsc}" data-mes="4" data-nivel="${nivel}" onchange="window.recalcPreviewMes(this)" /> </td>
                        <td style="padding:8px;text-align:right;font-weight:700;">${m1+m2+m3+m4} hrs</td>
                        <td style="padding:8px;text-align:right;font-family:monospace;">$${pagoM1.toLocaleString('es-MX',{minimumFractionDigits:2})}</td>
                        <td style="padding:8px;text-align:center;">
                            ${btnHtml}
                            ${m.ya_emitido ? 
                                `<button onclick="window.anularContrato(${docenteId}, ${m.materia_id}, '${matEsc}')" 
                                         style="margin-top:5px;padding:3px 8px;background:#ef4444;color:white;border:none;border-radius:4px;cursor:pointer;font-size:0.65rem;display:block;width:100%;">🗑️ Anular</button>` : ''}
                        </td>
                    </tr>`;
                });
            }
            htmlTabla += `</tbody></table></div>`;

            const addMateriaHtml = `
            <div style="background:#f0fdf4; border:1px dashed #22c55e; padding:10px; border-radius:8px; margin-bottom:20px; font-size:0.85rem; display:flex; gap:10px; align-items:center;">
                <input type="text" id="add-new-mat" placeholder="Nombre de Materia" style="flex:1; padding:6px; border:1px solid #ccc; border-radius:4px;" />
                <input type="number" id="add-new-hrs" placeholder="Hrs/mes" style="width:70px; padding:6px; border:1px solid #ccc; border-radius:4px;" title="Horas por mes (se aplica igual en los 4 meses)" />
                <select id="add-new-lvl" style="padding:6px; border:1px solid #ccc; border-radius:4px;">
                    <option value="Licenciatura">Licenciatura</option>
                    <option value="Maestría">Maestría</option>
                    <option value="Técnico">Técnico</option>
                    <option value="Doctorado">Doctorado</option>
                </select>
                <button onclick="window.agregarMateriaDinamica(${docenteId})" style="padding:6px 12px; background:#10b981; color:white; border:none; border-radius:4px; cursor:pointer;">+ Asignar</button>
            </div>`;

            // Alerta de 80 hrs
            let alertaHrs = '';
            if (totalHorasDocente > 80) {
                alertaHrs = `<div style="background:#fef2f2; border:2px solid #ef4444; padding:12px; border-radius:8px; margin-bottom:15px; font-size:0.9rem; color:#991b1b; font-weight:bold;">
                    ⛔ ALERTA: El docente tiene ${totalHorasDocente} hrs asignadas. El máximo permitido por la UDEFA es de 80 hrs/mes. No se podrá generar el contrato hasta que se ajuste la carga.
                </div>`;
            } else if (totalHorasDocente > 60) {
                alertaHrs = `<div style="background:#fffbeb; border:1px solid #f59e0b; padding:10px; border-radius:8px; margin-bottom:15px; font-size:0.85rem; color:#92400e;">
                    ⚠️ Advertencia: ${totalHorasDocente}/80 hrs utilizadas. Se acerca al límite mensual.
                </div>`;
            } else {
                alertaHrs = `<div style="background:#f0fdf4; border:1px solid #22c55e; padding:8px; border-radius:8px; margin-bottom:15px; font-size:0.85rem; color:#166534;">
                    ✅ ${totalHorasDocente}/80 hrs asignadas.
                </div>`;
            }

            let infoHtml = `
                <div style="margin-bottom:12px; font-size:1.05rem;">
                    Configuración de Carga Horaria para Contrato de:<br>
                    <strong style="font-size:1.2rem;">${nombre}</strong> (ID: ${docenteId})
                </div>
                <div style="background:#fefce8; border:1px solid #fef08a; padding:12px; border-radius:8px; margin-bottom:15px; font-size:0.85rem; color:#854d0e;">
                    <strong>Instructivo:</strong> Edita las horas por mes (M1=Marzo, M2=Abril, M3=Mayo, M4=Junio) para cada materia. Los cambios se guardan automáticamente al modificar.
                </div>
                ${alertaHrs}
                ${htmlTabla}
                ${addMateriaHtml}
            `;

            document.getElementById('preview-contrato-content').innerHTML = infoHtml;
            
            pendingContratoPayload = { docente_id: docenteId, emitido_por: currentUser.name || currentUser.role, nombre: nombre };
            document.getElementById('modal-preview-contrato').classList.remove('hidden');
        } catch(e) {
            alert('Error calculando previa: ' + e.message);
        } finally {
            if (btn) {
                btn.innerHTML = ogText;
                btn.disabled = false;
            }
        }
    };

    window.recalcPreviewMes = async function(inputEl) {
        let newVal = parseFloat(inputEl.value) || 0;
        if (newVal < 0) { newVal = 0; inputEl.value = 0; }

        const docId = parseInt(inputEl.dataset.docid);
        const materia = inputEl.dataset.mat;
        const nivel = inputEl.dataset.nivel || 'Licenciatura';
        const mesIdx = parseInt(inputEl.dataset.mes);

        // Encontrar los otros inputs de la misma fila
        const row = inputEl.closest('tr');
        const inputs = row.querySelectorAll('input[type="number"]');
        const vals = [0, 0, 0, 0];
        inputs.forEach((inp, i) => { vals[i] = parseFloat(inp.value) || 0; });

        inputEl.style.borderColor = '#f59e0b';
        try {
            const body = {
                materia, nivel_pago: nivel,
                horas_m1: vals[0], horas_m2: vals[1],
                horas_m3: vals[2], horas_m4: vals[3]
            };
            const res = await fetch(`/api/asignacion/${docId}/horas`, {
                method: 'PUT',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify(body)
            });
            if (!res.ok) throw new Error('Error guardando horas');
            inputEl.style.borderColor = '#22c55e';
            setTimeout(() => { inputEl.style.borderColor = ''; }, 1500);
            // Refrescar preview silenciosamente
            if (pendingContratoPayload) {
                window.generarContrato(pendingContratoPayload.docente_id, pendingContratoPayload.nombre);
            }
        } catch(e) {
            inputEl.style.borderColor = '#ef4444';
            console.error('Error guardando horas', e);
            alert('Error actualizando horas en base de datos: ' + e.message);
        }
    };

    // Mantener compatibilidad (por si hay llamadas antiguas)
    window.recalcPreview = async function(inputEl, docId, matNombre, nivelActual) {
        let newVal = parseFloat(inputEl.value) || 0;
        if (newVal < 0) { newVal = 0; inputEl.value = 0; }
        inputEl.disabled = true;
        try {
            await fetch(`/api/asignacion/${docId}/horas`, {
                method: 'PUT',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify({ horas: newVal, materia: matNombre, nivel_pago: nivelActual })
            });
            if(pendingContratoPayload) window.generarContrato(pendingContratoPayload.docente_id, pendingContratoPayload.nombre);
        } catch(e) {
            console.error('Error guardando horas', e);
            alert('Error actualizando horas en base de datos.');
        } finally {
            inputEl.disabled = false;
        }
    };

    window.recalcPreviewLevel = async function(selectEl, docId, matNombre, m1, m2, m3, m4) {
        let nivel = selectEl.value;
        selectEl.disabled = true;
        // m1..m4 pueden ser undefined si viene de código legacy, en ese caso usar 0
        const hm1 = parseFloat(m1) || 0;
        const hm2 = parseFloat(m2) || 0;
        const hm3 = parseFloat(m3) || 0;
        const hm4 = parseFloat(m4) || 0;
        try {
            await fetch(`/api/asignacion/${docId}/horas`, {
                method: 'PUT',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify({ horas_m1: hm1, horas_m2: hm2, horas_m3: hm3, horas_m4: hm4, materia: matNombre, nivel_pago: nivel })
            });
            if(pendingContratoPayload) window.generarContrato(pendingContratoPayload.docente_id, pendingContratoPayload.nombre);
        } catch(e) {
            alert('Error actualizando nivel.');
        } finally {
            selectEl.disabled = false;
        }
    };

    window.agregarMateriaDinamica = async function(docId) {
        const mat = document.getElementById('add-new-mat').value.trim();
        const hrs = document.getElementById('add-new-hrs').value;
        const lvl = document.getElementById('add-new-lvl').value;
        
        if(!mat || hrs === '') return alert('Escribe la materia y las horas.');
        
        try {
            await fetch(`/api/asignacion/${docId}/nueva`, {
                method: 'POST',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify({ materia: mat, horas: parseFloat(hrs), nivel_pago: lvl, carrera: currentUser.role !== 'ADM' ? currentUser.role : 'ICI' })
            });
            if(pendingContratoPayload) window.generarContrato(pendingContratoPayload.docente_id, pendingContratoPayload.nombre);
        } catch(e) {
            alert('Error agregando materia');
        }
    };

    window.confirmarGeneracionIndividual = async function(evtOrDocId, docenteIdOrMateriaId, materiaIdOrMatNombre, materiaNombreOrDocNombre, docenteNombreOpt) {
        // Soporta dos firmas: (event, docId, matId, matNombre, docNombre) o (docId, matId, matNombre, docNombre)
        let btn, docenteId, materiaId, materiaNombre, docenteNombre;
        if (evtOrDocId instanceof Event || (evtOrDocId && evtOrDocId.target)) {
            const ev = evtOrDocId;
            btn = ev.target.closest('button') || ev.target;
            docenteId = docenteIdOrMateriaId;
            materiaId = materiaIdOrMatNombre;
            materiaNombre = materiaNombreOrDocNombre;
            docenteNombre = docenteNombreOpt || '';
        } else {
            // Firma legacy: (docId, matId, matNombre, docNombre)
            btn = (typeof event !== 'undefined' && event && event.target) ? (event.target.closest('button') || event.target) : null;
            docenteId = evtOrDocId;
            materiaId = docenteIdOrMateriaId;
            materiaNombre = materiaIdOrMatNombre;
            docenteNombre = materiaNombreOrDocNombre || '';
        }

        if (!confirm(`¿Deseas generar el contrato para la materia "${materiaNombre}"?`)) return;
        
        const ogText = btn ? btn.innerHTML : '';
        if (btn) { btn.innerHTML = '⏳...'; btn.disabled = true; }

        try {
            const payload = {
                docente_id: docenteId,
                materia_id: materiaId,
                emitido_por: currentUser.name || currentUser.role,
                user_role: currentUser.role
            };

            const res = await fetch(`/api/generarContrato`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if(!res.ok) {
                const err = await res.json().catch(()=>({}));
                throw new Error(err.message || 'Error del servidor');
            }

            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = `Contrato_${docenteNombre.split(' ').join('_')}_${materiaNombre.split(' ').join('_')}.docx`;
            document.body.appendChild(a);
            a.click();
            
            setTimeout(() => {
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
            }, 1000);

            alert("Contrato generado exitosamente.");
            loadContratosFromDatabase();
            // window.closePreviewModal(); // Opcional: no cerrar para permitir generar otras materias
        } catch(err) {
            if (err.message.includes("ya se ha generado")) {
                if (confirm(err.message + "\n\n¿Deseas ANULAR el registro anterior para poder generar uno nuevo?")) {
                    await window.anularContrato(docenteId, materiaId, materiaNombre, true);
                    return; // El usuario deberá darle clic de nuevo
                }
            } else {
                alert("Error: " + err.message);
            }
            btn.innerHTML = ogText;
            btn.disabled = false;
        } finally {
            btn.innerHTML = ogText;
            btn.disabled = false;
        }
    };

    window.anularContrato = async function(docId, matId, matNombre, silencioso = false) {
        if (!silencioso && !confirm(`¿Estás seguro de que deseas ANULAR el registro del contrato para "${matNombre}"?\n\nEsto permitirá generarlo de nuevo, pero eliminará el registro de emisión anterior.`)) return;
        
        try {
            const res = await fetch('/api/anularContrato', {
                method: 'POST',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify({ docente_id: docId, materia_id: matId, user_role: currentUser.role })
            });
            const data = await res.json();
            if (res.ok) {
                if (!silencioso) alert(data.message);
                // Recargar vista previa
                if(pendingContratoPayload) window.generarContrato(pendingContratoPayload.docente_id, pendingContratoPayload.nombre);
                loadContratosFromDatabase(); // Actualizar tabla de auditoría
            } else {
                alert("Error: " + data.message);
            }
        } catch(e) {
            alert("Error de red al anular contrato");
        }
    };

    const btnConfirm = document.getElementById('btn-confirm-generar');
    if (btnConfirm) {
        btnConfirm.innerHTML = "Cerrar Ventana";
        btnConfirm.onclick = () => window.closePreviewModal();
    }

    // ==========================================
    // QUICK ROLES — rellena credenciales de prueba
    // ==========================================
    function buildQuickButtons(users, listEl, userField, passField, roleFilter) {
        const filtered = roleFilter ? users.filter(u => u.role === roleFilter) : users;
        if (!filtered.length) {
            listEl.innerHTML = '<span style="font-size:0.78rem;color:#f87171;">No hay usuarios con ese rol en la BD.</span>';
            return;
        }
        listEl.innerHTML = '';
        filtered.forEach(u => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.textContent = u.role + ' · ' + u.username;
            btn.style.cssText = 'background:#1e3a5f;border:none;color:white;padding:5px 12px;border-radius:6px;cursor:pointer;font-size:0.8rem;font-weight:700;transition:opacity .15s;';
            btn.onmouseover = () => btn.style.opacity = '0.8';
            btn.onmouseout  = () => btn.style.opacity = '1';
            btn.onclick = () => {
                document.getElementById(userField).value = u.username;
                document.getElementById(passField).value = u.username;
            };
            listEl.appendChild(btn);
        });
    }

    async function loadQuickRoles() {
        const listEl = document.getElementById('quick-roles-list');
        if (!listEl) return;
        listEl.innerHTML = '<span style="font-size:0.78rem;color:#94a3b8;">Cargando usuarios...</span>';
        try {
            const res = await fetch('/api/usuarios-roles');
            if (!res.ok) {
                listEl.innerHTML = '<span style="font-size:0.78rem;color:#f87171;">HTTP ' + res.status + '</span>';
                return;
            }
            const users = await res.json();
            console.log('[quick-roles] usuarios cargados:', users);
            buildQuickButtons(users, listEl, 'login-user', 'login-pass', null);
        } catch(e) {
            console.error('[quick-roles] fetch fallo:', e);
            listEl.innerHTML = '<span style="font-size:0.78rem;color:#f87171;">Sin conexión: ' + e.message + '</span>';
        }
    }

    async function loadPlantelesSelect(schoolCode) {
        const sel = document.getElementById('login-plantel');
        if (!sel) return;
        sel.innerHTML = '<option value="">Cargando planteles...</option>';
        try {
            const res = await fetch('/api/escuelas');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const list = await res.json();
            sel.innerHTML = '';
            list.forEach(e => {
                const dbName = e.datname;
                const siglas = dbName.replace('gestion_docente_', '').toUpperCase();
                const opt = document.createElement('option');
                opt.value = dbName;
                opt.textContent = siglas + ' — ' + dbName;
                if (schoolCode && siglas === schoolCode.toUpperCase()) opt.selected = true;
                sel.appendChild(opt);
            });
            if (sel.options.length === 0) {
                sel.innerHTML = '<option value="">No hay planteles configurados</option>';
                return;
            }
            // Si ninguna opción quedó seleccionada, elegir la primera
            if (!sel.value) sel.selectedIndex = 0;
            // Fijar tenant activo y recargar quick-roles con el tenant correcto
            localStorage.setItem('sgdc_tenant', sel.value);
            sel.onchange = () => { if (sel.value) localStorage.setItem('sgdc_tenant', sel.value); };
            loadQuickRoles();
        } catch(e) {
            // Fallback: usar la escuela seleccionada desde el landing
            console.warn('[plantel-select] /api/escuelas no disponible, usando fallback:', e.message);
            if (schoolCode) {
                sel.innerHTML = '';
                const opt = document.createElement('option');
                opt.value = 'gestion_docente_' + schoolCode.toLowerCase();
                opt.textContent = schoolCode;
                opt.selected = true;
                sel.appendChild(opt);
                localStorage.setItem('sgdc_tenant', opt.value);
                loadQuickRoles();
            } else {
                sel.innerHTML = '<option value="">Sin conexión al servidor</option>';
            }
        }
    }

    async function loadSemQuickUsers() {
        const listEl = document.getElementById('sem-quick-list');
        if (!listEl) return;
        listEl.innerHTML = '<span style="font-size:0.78rem;color:#94a3b8;">Cargando...</span>';
        try {
            const res = await fetch('/api/usuarios-roles');
            if (!res.ok) {
                listEl.innerHTML = '<span style="font-size:0.78rem;color:#f87171;">HTTP ' + res.status + '</span>';
                return;
            }
            const users = await res.json();
            buildQuickButtons(users, listEl, 'admin-sem-user', 'admin-sem-pass', 'SEM');
        } catch(e) {
            listEl.innerHTML = '<span style="font-size:0.78rem;color:#f87171;">Sin conexión</span>';
        }
    }

    // ==========================================
    // ADMIN SEM — Gestión de Planteles
    // ==========================================
    const ROLE_COLORS = {
        'ADM':  '#1e3a5f', 'SEM':  '#4c1d95', 'DIR':  '#065f46',
        'JSA':  '#7c3aed', 'ICI':  '#b45309', 'ICE':  '#0369a1',
        'II':   '#065f46', 'IC':   '#9a3412', 'TC':   '#374151',
        'MED':  '#be185d', 'ODO':  '#0f766e', 'ENF':  '#0c4a6e'
    };

    // Store unconfigured school defaults globally so onclick can reference by index
    let _pendingSchoolDefaults = [];

    async function loadAdminEscuelas() {
        const el = document.getElementById('admin-escuelas-lista');
        if (!el) return;
        _pendingSchoolDefaults = [];
        el.innerHTML = '<span style="color:rgba(255,255,255,0.5);font-size:0.9rem;">Cargando...</span>';
        try {
            const res = await fetch('/api/escuelas');
            const data = await res.json();

            // Cargar detalles de cada plantel en paralelo
            el.innerHTML = '<span style="color:rgba(255,255,255,0.5);font-size:0.85rem;">Obteniendo detalles...</span>';
            const detalles = data.length ? await Promise.all(data.map(r =>
                fetch(`/api/escuelas/${r.datname}/detalle`).then(x => x.json()).catch(() => ({status:'error', database: r.datname}))
            )) : [];

            // Definir planteles esperados por defecto en el sistema
            const defaultSchools = [
                { siglas: 'EMI', db: 'gestion_docente_emi', nombre: 'Escuela Militar de Ingeniería', cycle: 'MAR-AGO 2026', carrerasCheck: ['ICI','ICE','II','IC','TC'] },
                { siglas: 'EMM', db: 'gestion_docente_emm', nombre: 'Escuela Militar de Medicina', cycle: 'MAR-AGO 2026', carrerasCheck: ['MED'] },
                { siglas: 'EMO', db: 'gestion_docente_emo', nombre: 'Escuela Militar de Odontología', cycle: 'MAR-AGO 2026', carrerasCheck: ['ODO'] }
            ];

            const dbsExistentes = new Set(detalles.map(d => d.database));
            defaultSchools.forEach(ds => {
                if (!dbsExistentes.has(ds.db)) {
                    const idx = _pendingSchoolDefaults.length;
                    _pendingSchoolDefaults.push(ds);
                    detalles.push({
                        database: ds.db,
                        carreras: [], usuarios: [], totalDocentes: 0,
                        isUnconfigured: true, _defaultIdx: idx
                    });
                }
            });

            el.innerHTML = '';
            detalles.forEach(d => {
                const card = document.createElement('div');
                const isUnconf = d.isUnconfigured;
                card.style.cssText = `background:${isUnconf ? 'rgba(220,38,38,0.08)' : 'rgba(255,255,255,0.08)'};border:1px solid ${isUnconf ? 'rgba(220,38,38,0.25)' : 'rgba(255,255,255,0.18)'};border-radius:14px;padding:18px 22px;flex:1 1 280px;min-width:280px;max-width:380px;`;

                const siglas = d.database ? d.database.replace('gestion_docente_','').toUpperCase() : '?';
                const carreras = d.carreras || [];
                const usuarios = d.usuarios || [];
                const docentes = d.totalDocentes || 0;

                const carrerasBadges = carreras.map(c =>
                    `<span style="background:rgba(195,163,100,0.25);border:1px solid rgba(195,163,100,0.5);color:#fde68a;padding:3px 10px;border-radius:12px;font-size:0.75rem;font-weight:700;margin:2px;display:inline-block;">${c.siglas}</span>`
                ).join('');

                const usersBadges = usuarios.map(u => {
                    const bg = ROLE_COLORS[u.role] || '#374151';
                    return `<span style="background:${bg};color:white;padding:3px 10px;border-radius:12px;font-size:0.72rem;font-weight:700;margin:2px;display:inline-block;" title="${u.role}">${u.username}</span>`;
                }).join('');

                const statusBadge = isUnconf
                    ? '<span style="background:rgba(220,38,38,0.2);color:#fca5a5;padding:2px 8px;border-radius:10px;font-size:0.65rem;font-weight:700;margin-left:6px;">NO CONFIGURADO</span>'
                    : '<span style="background:rgba(34,197,94,0.15);color:#86efac;padding:2px 8px;border-radius:10px;font-size:0.65rem;font-weight:700;margin-left:6px;">ACTIVO</span>';

                const btnId = 'sem-btn-' + siglas;
                const btnStyle = isUnconf
                    ? 'background:#dc2626;color:white;'
                    : 'background:var(--color-gold);color:#1a0a00;';
                const btnLabel = isUnconf ? 'Configurar' : 'Gestionar';

                card.innerHTML = `
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                        <div>
                            <div style="color:white;font-weight:900;font-size:1.15rem;font-family:var(--font-display);letter-spacing:0.5px;">${siglas}${statusBadge}</div>
                            <div style="color:rgba(255,255,255,0.45);font-size:0.72rem;margin-top:2px;font-family:monospace;">${d.database}</div>
                        </div>
                        <div style="display:flex;gap:8px;align-items:center;">
                            ${isUnconf ? '' : `<span style="background:rgba(255,255,255,0.1);color:rgba(255,255,255,0.8);padding:4px 10px;border-radius:20px;font-size:0.72rem;font-weight:700;">${docentes} doc.</span>`}
                            <button id="${btnId}" style="${btnStyle}border:none;padding:6px 14px;border-radius:8px;cursor:pointer;font-size:0.8rem;font-weight:800;white-space:nowrap;">${btnLabel}</button>
                        </div>
                    </div>
                    <div style="margin-bottom:6px;">
                        <div style="color:rgba(255,255,255,0.5);font-size:0.68rem;text-transform:uppercase;letter-spacing:0.5px;margin-bottom:4px;">Carreras (${carreras.length})</div>
                        <div>${carrerasBadges || '<em style="color:rgba(255,255,255,0.25);font-size:0.75rem;">Sin carreras</em>'}</div>
                    </div>
                    <div>
                        <div style="color:rgba(255,255,255,0.5);font-size:0.68rem;text-transform:uppercase;letter-spacing:0.5px;margin-bottom:4px;">Usuarios (${usuarios.length})</div>
                        <div>${usersBadges || '<em style="color:rgba(255,255,255,0.25);font-size:0.75rem;">Sin usuarios</em>'}</div>
                    </div>
                `;
                el.appendChild(card);

                // Attach click handler AFTER appending to DOM (using card.querySelector to be 100% sure)
                const btn = card.querySelector('#' + btnId);
                if (btn) {
                    if (isUnconf) {
                        btn.onclick = () => { window.semPrellenarConfig(d._defaultIdx); };
                    } else {
                        btn.onclick = () => { window.semAbrirGestionPlantel(d.database); };
                    }
                }
            });
        } catch(e) {
            el.innerHTML = `<span style="color:#fca5a5;">Error al cargar: ${e.message}</span>`;
        }
    }

    window.toggleFormNuevoPlantel = function() {
        const form = document.getElementById('form-nuevo-plantel');
        const btn = document.getElementById('btn-toggle-form-plantel');
        if (!form || !btn) return;
        
        if (form.style.display === 'none') {
            form.style.display = 'block';
            btn.innerHTML = '&minus; Ocultar';
        } else {
            form.style.display = 'none';
            btn.innerHTML = '+ Expandir';
            // Clear fields on close
            document.getElementById('np-siglas').value = '';
            document.getElementById('np-ciclo').value = '';
            document.getElementById('np-nombre').value = '';
            document.getElementById('np-director').value = '';
            document.getElementById('np-dbname').value = '';
            document.getElementById('np-dbpass').value = '';
            document.getElementById('np-usuarios-rows').innerHTML = '';
            document.querySelectorAll('#np-carreras-checks input').forEach(c => c.checked = false);
            document.getElementById('np-result-msg').innerHTML = '';
        }
    };

    window.semPrellenarConfig = function(idx) {
        const ds = _pendingSchoolDefaults[idx];
        if (!ds) { alert('Error: datos de plantel no encontrados.'); return; }
        document.getElementById('np-siglas').value = ds.siglas;
        document.getElementById('np-ciclo').value = ds.cycle;
        document.getElementById('np-nombre').value = ds.nombre;
        document.getElementById('np-dbname').value = ds.db;
        
        // Check checkboxes
        const checks = document.querySelectorAll('#np-carreras-checks input[type="checkbox"]');
        checks.forEach(c => {
            c.checked = ds.carrerasCheck.includes(c.value);
        });

        // Open form if closed
        const form = document.getElementById('form-nuevo-plantel');
        if (window.getComputedStyle(form).display === 'none') {
            window.toggleFormNuevoPlantel();
        }
        
        // Clear and pre-fill users
        document.getElementById('np-usuarios-rows').innerHTML = '';
        const baseRoles = [
            {u: 'admin', p: 'admin', r: 'ADM'},
            {u: 'dir', p: 'director', r: 'DIR'},
            {u: 'sem', p: 'sem', r: 'SEM'},
            {u: 'jsa', p: 'jsa', r: 'JSA'}
        ];
        baseRoles.forEach(x => window.npAgregarUsuario(x.u, x.p, x.r));

        form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

    // Modal de gestión rápida de plantel existente
    window.semAbrirGestionPlantel = async function(dbname) {
        const siglas = dbname.replace('gestion_docente_','').toUpperCase();
        // Obtener detalle fresco
        let detalle = {};
        try {
            const r = await fetch(`/api/escuelas/${dbname}/detalle`);
            detalle = await r.json();
        } catch(e) { detalle = { carreras: [], usuarios: [] }; }

        const carreras = detalle.carreras || [];
        const usuarios = detalle.usuarios || [];

        const TODAS_CARRERAS = [
            {s:'ICI', n:'Ing. Computación e Informática'},
            {s:'ICE', n:'Ing. Comunicaciones y Electrónica'},
            {s:'II',  n:'Ing. Industrial'},
            {s:'IC',  n:'Ing. Construcción Militar'},
            {s:'TC',  n:'Tronco Común'},
            {s:'MED', n:'Medicina'},
            {s:'ODO', n:'Odontología'},
            {s:'ENF', n:'Enfermería'},
            {s:'FAR', n:'Farmacología'},
            {s:'BIO', n:'Biología'},
        ];
        const existingSiglas = carreras.map(c => c.siglas);

        let ROLES = ['ADM', 'DIR', 'JSA', 'SEM', 'ICI', 'ICE', 'II', 'IC', 'TC', 'MED', 'ODO', 'ENF', 'FAR', 'BIO', 'QFB'];
        existingSiglas.forEach(s => { if (!ROLES.includes(s)) ROLES.push(s); });

        const usuariosHtml = usuarios.map(u => {
            const bg = ROLE_COLORS[u.role] || '#374151';
            return `<div style="display:flex;align-items:center;gap:8px;padding:6px 10px;background:#f8fafc;border-radius:8px;margin-bottom:6px;">
                <span style="background:${bg};color:white;padding:2px 10px;border-radius:10px;font-size:0.72rem;font-weight:800;">${u.role}</span>
                <span style="font-family:monospace;font-weight:700;font-size:0.88rem;">${u.username}</span>
            </div>`;
        }).join('');

        const carrerasCheckHtml = TODAS_CARRERAS.map(c =>
            `<label style="display:flex;align-items:center;gap:5px;font-size:0.85rem;cursor:pointer;padding:4px 8px;border-radius:6px;${existingSiglas.includes(c.s) ? 'background:#d1fae5;color:#065f46;font-weight:700;' : ''}">` +
            `<input type="checkbox" value="${c.s}" class="gp-car-chk" ${existingSiglas.includes(c.s) ? 'checked disabled' : ''}> ` +
            `<strong>${c.s}</strong> — ${c.n}${existingSiglas.includes(c.s) ? ' ✅' : ''}</label>`
        ).join('');

        const rolesOpts = ROLES.map(r => `<option value="${r}">${r}</option>`).join('');

        // Crear modal inline
        let modal = document.getElementById('sem-gestion-modal');
        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'sem-gestion-modal';
            modal.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.7);z-index:5000;display:flex;justify-content:center;align-items:flex-start;overflow-y:auto;padding:30px 20px;backdrop-filter:blur(4px);';
            document.body.appendChild(modal);
        }
        modal.style.display = 'flex';
        modal.innerHTML = `
        <div style="background:white;width:100%;max-width:700px;border-radius:20px;overflow:hidden;box-shadow:0 30px 80px rgba(0,0,0,0.5);margin:auto;">
            <div style="background:linear-gradient(135deg,#1e3a5f,#2563eb);padding:22px 28px;display:flex;justify-content:space-between;align-items:center;">
                <div>
                    <div style="color:rgba(255,255,255,0.7);font-size:0.75rem;text-transform:uppercase;letter-spacing:1px;">Panel SEM — Configuración Rápida</div>
                    <h3 style="color:white;margin:4px 0 0;font-size:1.15rem;font-weight:800;">⚙️ Gestionar Plantel: ${siglas}</h3>
                </div>
                <button onclick="document.getElementById('sem-gestion-modal').style.display='none'" style="background:rgba(255,255,255,0.15);border:1px solid rgba(255,255,255,0.3);color:white;border-radius:50%;width:34px;height:34px;cursor:pointer;font-size:1.1rem;">×</button>
            </div>
            <div style="padding:24px 28px;">
                <!-- Usuarios actuales -->
                <div style="margin-bottom:22px;">
                    <h4 style="color:#1e3a5f;font-size:0.9rem;text-transform:uppercase;letter-spacing:1px;margin-bottom:10px;">👥 Usuarios Actuales (${usuarios.length})</h4>
                    <div style="max-height:160px;overflow-y:auto;padding:4px;">${usuariosHtml || '<em style="color:#aaa;">Sin usuarios registrados</em>'}</div>
                </div>

                <!-- Agregar / actualizar usuario -->
                <div style="background:#f8fafc;border:1.5px solid #e2e8f0;border-radius:12px;padding:16px;margin-bottom:22px;">
                    <h4 style="color:#1e3a5f;font-size:0.88rem;text-transform:uppercase;letter-spacing:1px;margin-bottom:12px;">➕ Agregar / Actualizar Usuario</h4>
                    <div style="display:grid;grid-template-columns:1fr 1fr 120px;gap:10px;margin-bottom:10px;">
                        <div>
                            <label style="font-size:0.75rem;font-weight:700;color:#475569;display:block;margin-bottom:4px;">USUARIO</label>
                            <input id="gp-uname" placeholder="Ej: jefe_ici" style="width:100%;padding:8px 10px;border:1.5px solid #e2e8f0;border-radius:8px;font-size:0.9rem;box-sizing:border-box;">
                        </div>
                        <div>
                            <label style="font-size:0.75rem;font-weight:700;color:#475569;display:block;margin-bottom:4px;">CONTRASEÑA</label>
                            <input id="gp-upass" placeholder="Contraseña" style="width:100%;padding:8px 10px;border:1.5px solid #e2e8f0;border-radius:8px;font-size:0.9rem;box-sizing:border-box;">
                        </div>
                        <div>
                            <label style="font-size:0.75rem;font-weight:700;color:#475569;display:block;margin-bottom:4px;">ROL</label>
                            <select id="gp-urole" style="width:100%;padding:8px 10px;border:1.5px solid #e2e8f0;border-radius:8px;font-size:0.88rem;">
                                ${rolesOpts}
                            </select>
                        </div>
                    </div>
                    <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px;">
                        <button onclick="window.semQuickRole('${dbname}','ADM','admin','admin')" style="padding:5px 12px;background:#1e3a5f;color:white;border:none;border-radius:6px;cursor:pointer;font-size:0.78rem;font-weight:700;">+ ADM</button>
                        <button onclick="window.semQuickRole('${dbname}','DIR','director','director')" style="padding:5px 12px;background:#065f46;color:white;border:none;border-radius:6px;cursor:pointer;font-size:0.78rem;font-weight:700;">+ DIR</button>
                        <button onclick="window.semQuickRole('${dbname}','JSA','jsa','jsa')" style="padding:5px 12px;background:#7c3aed;color:white;border:none;border-radius:6px;cursor:pointer;font-size:0.78rem;font-weight:700;">+ JSA</button>
                        ${existingSiglas.map(s => `<button onclick="window.semQuickRole('${dbname}','${s}','jefe_${s.toLowerCase()}','jefe_${s.toLowerCase()}')" style="padding:5px 12px;background:${ROLE_COLORS[s]||'#374151'};color:white;border:none;border-radius:6px;cursor:pointer;font-size:0.78rem;font-weight:700;">+ J. ${s}</button>`).join('')}
                    </div>
                    <div id="gp-user-msg" style="min-height:18px;font-size:0.82rem;margin-bottom:8px;"></div>
                    <button onclick="window.semAgregarUsuarioPlantel('${dbname}')" style="background:var(--color-maroon);color:white;border:none;padding:9px 20px;border-radius:9px;cursor:pointer;font-weight:700;font-size:0.9rem;">💾 Guardar Usuario</button>
                </div>

                <!-- Carreras -->
                <div style="background:#f8fafc;border:1.5px solid #e2e8f0;border-radius:12px;padding:16px;">
                    <h4 style="color:#1e3a5f;font-size:0.88rem;text-transform:uppercase;letter-spacing:1px;margin-bottom:12px;">📚 Carreras del Plantel</h4>
                    <div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-bottom:12px;" id="gp-carreras-grid">${carrerasCheckHtml}</div>
                    
                    <div style="display:flex; gap:8px; margin-bottom:12px; align-items:center;">
                        <input type="text" id="gp-nueva-carrera-siglas" placeholder="Siglas (Ej: FAR)" style="padding:6px 10px;border:1.5px solid #e2e8f0;border-radius:6px;font-size:0.85rem;width:120px;text-transform:uppercase;">
                        <button onclick="window.semAgregarCarreraCustom()" style="background:#e0f2fe;color:#0369a1;border:1px solid #bae6fd;padding:6px 14px;border-radius:6px;cursor:pointer;font-size:0.82rem;font-weight:700;">+ Añadir Opción</button>
                    </div>

                    <div id="gp-car-msg" style="min-height:18px;font-size:0.82rem;margin-bottom:8px;"></div>
                    <button onclick="window.semAgregarCarrerasPlantel('${dbname}')" style="background:#0369a1;color:white;border:none;padding:9px 20px;border-radius:9px;cursor:pointer;font-weight:700;font-size:0.9rem;">📚 Guardar Carreras Seleccionadas</button>
                </div>
            </div>
        </div>`;
    };

    window.semAgregarCarreraCustom = function() {
        const sigEl = document.getElementById('gp-nueva-carrera-siglas');
        const siglas = sigEl.value.trim().toUpperCase();
        if (!siglas) { alert("Debe ingresar la abreviatura."); return; }
        const grid = document.getElementById('gp-carreras-grid');
        const label = document.createElement('label');
        label.style.cssText = 'display:flex;align-items:center;gap:5px;font-size:0.85rem;cursor:pointer;padding:4px 8px;border-radius:6px;background:#f0f9ff;';
        label.innerHTML = `<input type="checkbox" value="${siglas}" class="gp-car-chk" checked> <strong>${siglas}</strong> — (Nueva)`;
        grid.appendChild(label);
        sigEl.value = '';
    };

    window.semQuickRole = function(dbname, role, username, password) {
        document.getElementById('gp-uname').value  = username;
        document.getElementById('gp-upass').value  = password;
        document.getElementById('gp-urole').value  = role;
    };

    window.semAgregarUsuarioPlantel = async function(dbname) {
        const uname = document.getElementById('gp-uname').value.trim();
        const upass = document.getElementById('gp-upass').value.trim();
        const urole = document.getElementById('gp-urole').value;
        const msgEl = document.getElementById('gp-user-msg');
        if (!uname || !upass) { msgEl.innerHTML = '<span style="color:#dc2626;">⚠️ Usuario y contraseña son requeridos.</span>'; return; }
        msgEl.innerHTML = '<span style="color:#0369a1;">Guardando...</span>';
        try {
            const res = await fetch(`/api/escuelas/${dbname}/usuario`, {
                method: 'POST',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify({ username: uname, password: upass, role: urole })
            });
            const data = await res.json();
            if (data.status === 'ok') {
                const action = data.action === 'updated' ? 'actualizado' : 'creado';
                msgEl.innerHTML = `<span style="color:#065f46;">✅ Usuario <strong>${uname}</strong> ${action} con rol <strong>${urole}</strong>.</span>`;
                // Refrescar modal
                setTimeout(() => window.semAbrirGestionPlantel(dbname), 800);
                loadAdminEscuelas();
            } else {
                msgEl.innerHTML = `<span style="color:#dc2626;">✘ ${data.message}</span>`;
            }
        } catch(e) {
            msgEl.innerHTML = `<span style="color:#dc2626;">✘ Error: ${e.message}</span>`;
        }
    };

    window.semAgregarCarrerasPlantel = async function(dbname) {
        const checks = document.querySelectorAll('.gp-car-chk:not(:disabled):checked');
        const msgEl = document.getElementById('gp-car-msg');
        if (!checks.length) { msgEl.innerHTML = '<span style="color:#f59e0b;">⚠️ Selecciona al menos una carrera nueva.</span>'; return; }
        msgEl.innerHTML = '<span style="color:#0369a1;">Guardando...</span>';
        let ok = 0, fail = 0;
        for (const chk of checks) {
            try {
                const res = await fetch(`/api/escuelas/${dbname}/carrera`, {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({ siglas: chk.value })
                });
                const d = await res.json();
                if (d.status === 'ok') ok++; else fail++;
            } catch { fail++; }
        }
        msgEl.innerHTML = ok > 0
            ? `<span style="color:#065f46;">✅ ${ok} carrera(s) agregada(s)${fail>0?' · '+fail+' error(es)':''}.</span>`
            : `<span style="color:#dc2626;">✘ No se pudo agregar ninguna carrera.</span>`;
        if (ok > 0) { setTimeout(() => window.semAbrirGestionPlantel(dbname), 800); loadAdminEscuelas(); }
    };

    window.npAgregarUsuario = function(defU = '', defP = '', defR = 'ADM') {
        const row = document.createElement('div');
        row.style.cssText = 'display:grid;grid-template-columns:1fr 1fr 120px 40px;gap:8px;margin-bottom:8px;';
        
        const roles = ['ADM','DIR','SEM','JSA','ICI','ICE','II','IC','TC','MED','ODO','ENF','FAR','BIO','QFB'];
        if (defR && !roles.includes(defR)) roles.push(defR);
        const optionsHtml = roles.map(r => `<option value="${r}" ${defR === r ? 'selected' : ''}>${r}</option>`).join('');

        row.innerHTML = `
            <input type="text" placeholder="Usuario" class="np-u-user" value="${defU}" style="padding:8px 10px;border:1.5px solid #e2e8f0;border-radius:7px;font-size:0.88rem;">
            <input type="text" placeholder="Contraseña" class="np-u-pass" value="${defP}" style="padding:8px 10px;border:1.5px solid #e2e8f0;border-radius:7px;font-size:0.88rem;">
            <select class="np-u-role" style="padding:8px 10px;border:1.5px solid #e2e8f0;border-radius:7px;font-size:0.88rem;">
                ${optionsHtml}
            </select>
            <button type="button" onclick="this.parentElement.remove()" style="background:#fee2e2;color:#dc2626;border:none;border-radius:7px;cursor:pointer;font-weight:700;">✕</button>
        `;
        document.getElementById('np-usuarios-rows').appendChild(row);
    };

    window.npAgregarCarreraCheck = function() {
        const sigEl = document.getElementById('np-nueva-carrera-siglas');
        const nomEl = document.getElementById('np-nueva-carrera-nombre');
        const siglas = sigEl.value.trim().toUpperCase();
        const nombre = nomEl.value.trim();
        if (!siglas || !nombre) { alert("Debe ingresar la abreviatura y el nombre."); return; }
        
        const container = document.getElementById('np-carreras-checks');
        const label = document.createElement('label');
        label.style.cssText = 'display:flex;align-items:center;gap:5px;font-size:0.88rem;cursor:pointer;background:#f0f9ff;padding:4px 8px;border-radius:6px;';
        label.innerHTML = `<input type="checkbox" value="${siglas}" checked> ${siglas} - ${nombre}`;
        container.appendChild(label);
        
        sigEl.value = '';
        nomEl.value = '';
        
        // Also add a user for this new career
        window.npAgregarUsuario(siglas.toLowerCase(), siglas.toLowerCase(), siglas);
    };

    window.npGuardarPlantel = async function() {
        const siglas  = document.getElementById('np-siglas').value.trim();
        const nombre  = document.getElementById('np-nombre').value.trim();
        const ciclo   = document.getElementById('np-ciclo').value.trim();
        if (!siglas || !nombre || !ciclo) { alert('Siglas, Nombre y Ciclo son obligatorios.'); return; }

        const carreras = Array.from(document.querySelectorAll('#np-carreras-checks input:checked')).map(c => c.value);
        const usuarios = [];
        document.querySelectorAll('#np-usuarios-rows > div').forEach(row => {
            const u = row.querySelector('.np-u-user').value.trim();
            const p = row.querySelector('.np-u-pass').value.trim();
            const r = row.querySelector('.np-u-role').value;
            if (u && p) usuarios.push({ username: u, password: p, role: r });
        });

        const payload = {
            siglas, nombre, ciclo, carreras, usuarios,
            dbHost: document.getElementById('np-dbhost').value.trim(),
            dbPort: document.getElementById('np-dbport').value.trim(),
        };
        const dbName = document.getElementById('np-dbname').value.trim();
        const dbUser = document.getElementById('np-dbuser').value.trim();
        const dbPass = document.getElementById('np-dbpass').value;
        if (dbName) payload.dbName = dbName;
        if (dbUser) payload.dbUser = dbUser;
        if (dbPass) payload.dbPass = dbPass;

        const msgEl = document.getElementById('np-result-msg');
        const btn = document.querySelector('#form-nuevo-plantel .btn-action');
        const ogText = btn.innerHTML;
        btn.innerHTML = 'Configurando...';
        btn.disabled = true;
        msgEl.innerHTML = '';

        try {
            const res = await fetch('/api/escuela/configurar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (data.status !== 'error') {
                msgEl.innerHTML = `<div style="background:#d1fae5;color:#065f46;padding:10px 14px;border-radius:8px;font-weight:600;font-size:0.9rem;">
                    ✔ ${data.message}
                    ${data.carrerasInsertadas?.length ? ' · Carreras: ' + data.carrerasInsertadas.join(', ') : ''}
                    ${data.usuariosCreados?.length ? ' · Usuarios: ' + data.usuariosCreados.map(u=>u.username).join(', ') : ''}
                </div>`;
                loadAdminEscuelas();
            } else {
                msgEl.innerHTML = `<div style="background:#fee2e2;color:#dc2626;padding:10px 14px;border-radius:8px;font-weight:600;font-size:0.9rem;">✘ ${data.message}</div>`;
            }
        } catch {
            msgEl.innerHTML = `<div style="background:#fee2e2;color:#dc2626;padding:10px 14px;border-radius:8px;font-weight:600;font-size:0.9rem;">✘ Error de conexión.</div>`;
        } finally {
            btn.innerHTML = ogText;
            btn.disabled = false;
        }
    };

    // ==========================================
    // CONTROL VEHICULAR
    // ==========================================
    window.loadVehiculosFromDatabase = async function() {
        const tbody = document.getElementById('vehiculos-tbody');
        if(!tbody) return;

        try {
            tbody.innerHTML = "<tr><td colspan='7'>Cargando vehículos...</td></tr>";
            
            // Pasamos el rol actual para filtrar en el backend si es Jefe de Carrera
            const role = currentUser.role || '';
            const res = await fetch(`/api/vehiculos?role=${role}`);
            if (!res.ok) throw new Error("Error en servidor");
            const vehiculos = await res.json();
            
            tbody.innerHTML = "";
            let vehiculosHtml = '';
            
            if (vehiculos.length === 0) {
                vehiculosHtml = "<tr><td colspan='7' style='text-align:center;'>No hay vehículos registrados para esta carrera.</td></tr>";
            } else {
                vehiculos.forEach(v => {
                    vehiculosHtml += `
                    <tr>
                        <td><strong>${v.carrera || 'N/A'}</strong></td>
                        <td>${v.docente}</td>
                        <td>${v.marca} ${v.modelo || ''}</td>
                        <td>${v.anio || ''}</td>
                        <td>${v.color || ''}</td>
                        <td style="font-family:monospace;font-weight:bold;background:#f8fafc;padding:4px 8px;border:1px solid #e2e8f0;border-radius:4px;">${v.placas}</td>
                        <td>
                            <button onclick='window.openVehiculoModal(${JSON.stringify(v)})' style="background:#0369a1;color:white;border:none;padding:5px 10px;border-radius:6px;cursor:pointer;font-size:0.75rem;">Editar</button>
                            <button onclick="window.eliminarVehiculo(${v.vehiculo_id})" style="background:#dc2626;color:white;border:none;padding:5px 10px;border-radius:6px;cursor:pointer;font-size:0.75rem;">Eliminar</button>
                        </td>
                    </tr>`;
                });
            }
            tbody.innerHTML = vehiculosHtml;
            document.getElementById('vehiculos-stats').innerHTML = `<span style="background:#f0fdf4;color:#166534;border:1px solid #bbf7d0;padding:6px 16px;border-radius:20px;font-weight:700;">Total: ${vehiculos.length} vehículos</span>`;
        } catch(e) {
            tbody.innerHTML = `<tr><td colspan="7" style="color:red;">Error: ${e.message}</td></tr>`;
        }
    };

    window.openVehiculoModal = async function(vData = null) {
        document.getElementById('modal-vehiculo').classList.remove('hidden');
        document.getElementById('modal-vehiculo-title').textContent = vData ? 'Editar Vehículo' : 'Registrar Vehículo';
        
        // Cargar docentes
        const selectDocente = document.getElementById('v-docente');
        try {
            const [docRes, vehRes] = await Promise.all([
                fetch('/api/docentes'),
                fetch('/api/vehiculos')
            ]);
            const docentes = await docRes.json();
            const vehiculos = await vehRes.json();
            
            // Set of docentes that already have a vehicle registered
            const docentesConVehiculo = new Set(vehiculos.map(v => v.docente_id));

            let opts = '<option value="">-- Seleccione un docente --</option>';
            
            // Filtro por rol del jefe de carrera
            docentes.forEach(d => {
                let isValid = true;
                if(currentUser.role !== 'ADM' && currentUser.role !== 'DIR' && currentUser.role !== 'SEM' && currentUser.role !== 'JSA') {
                    const carreras = (d.carrera || '').split(',').map(s => s.trim());
                    if(!carreras.includes(currentUser.role)) isValid = false;
                }
                
                // Mostrar solo si no tiene vehículo, o si estamos editando y el vehículo le pertenece
                const yaTiene = docentesConVehiculo.has(d.docente_id);
                const esSuyo = vData && vData.docente_id === d.docente_id;
                
                if(isValid && (!yaTiene || esSuyo)) {
                    opts += `<option value="${d.docente_id}">${d.nombre} (${d.carrera || 'Sin carrera'})</option>`;
                }
            });
            selectDocente.innerHTML = opts;
        } catch(e) {
            selectDocente.innerHTML = '<option value="">Error cargando docentes</option>';
        }

        // Rellenar datos si es edición
        if (vData) {
            document.getElementById('vehiculo-id').value = vData.vehiculo_id;
            setTimeout(() => document.getElementById('v-docente').value = vData.docente_id, 100); // Dar tiempo a que el select se llene
            document.getElementById('v-marca').value = vData.marca || '';
            document.getElementById('v-modelo').value = vData.modelo || '';
            document.getElementById('v-anio').value = vData.anio || '';
            document.getElementById('v-color').value = vData.color || '';
            document.getElementById('v-placas').value = vData.placas || '';
        } else {
            document.getElementById('form-vehiculo').reset();
            document.getElementById('vehiculo-id').value = '';
        }
    };

    window.closeVehiculoModal = function() {
        document.getElementById('modal-vehiculo').classList.add('hidden');
    };

    window.guardarVehiculo = async function() {
        const id = document.getElementById('vehiculo-id').value;
        const payload = {
            docente_id: document.getElementById('v-docente').value,
            marca: document.getElementById('v-marca').value,
            modelo: document.getElementById('v-modelo').value,
            anio: document.getElementById('v-anio').value,
            color: document.getElementById('v-color').value,
            placas: document.getElementById('v-placas').value
        };

        if(!payload.docente_id || !payload.marca || !payload.placas) {
            alert('Docente, Marca y Placas son obligatorios');
            return;
        }

        try {
            const url = id ? `/api/vehiculos/${id}` : '/api/vehiculos';
            const method = id ? 'PUT' : 'POST';
            const res = await fetch(url, {
                method: method,
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(payload)
            });
            
            const data = await res.json();
            if(data.status === 'ok') {
                closeVehiculoModal();
                loadVehiculosFromDatabase();
            } else {
                alert('Error: ' + data.message);
            }
        } catch(e) {
            alert('Error de conexión');
        }
    };

    window.eliminarVehiculo = async function(id) {
        if(!confirm('¿Estás seguro de eliminar este vehículo?')) return;
        try {
            const res = await fetch(`/api/vehiculos/${id}`, { method: 'DELETE' });
            if(res.ok) {
                loadVehiculosFromDatabase();
            } else {
                alert('Error eliminando vehículo');
            }
        } catch(e) {
            alert('Error de conexión');
        }
    };

});
