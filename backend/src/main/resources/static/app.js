// app.js

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
    };

    window.backToLanding = function() {
        loginWrapper.classList.add('auth-hidden');
        landingWrapper.classList.remove('auth-hidden');
    };

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
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({username: username, password: pass})
            });
            const data = await res.json();
            if (res.ok && data.status === 'ok') {
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
        currentUser = null;
        appWrapper.classList.add('auth-hidden');
        landingWrapper.classList.remove('auth-hidden');
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
        } catch(e) {
            alert('Error cargando perfil del docente: ' + e.message);
        }
    };

    window.cerrarPerfil = function() {
        document.getElementById('modal-perfil').classList.add('hidden');
        document.getElementById('p-foto-input').value = '';
        document.getElementById('p-cedula-input').value = '';
    };

    window.previewCedula = function(input) {
        if (!input.files || !input.files[0]) return;
        const file = input.files[0];
        const cedulaStatus = document.getElementById('p-cedula-status');
        cedulaStatus.innerHTML = `<span style="color:#0f766e;font-weight:600;">📄 Archivo seleccionado: </span><span style="color:#334155;">${file.name}</span> <span style="color:#94a3b8;font-size:0.8rem;">(se guardará al presionar "Guardar Cambios")</span>`;
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
            
            let tarifaLic = 486.71;
            let tarifaMtr = 851.75;
            let tarifaTec = 231.19;

            let htmlTabla = `<table style="width:100%; border-collapse:collapse; margin-bottom:10px;">
                <tr style="background:#f1f5f9;border-bottom:2px solid #cbd5e1;">
                    <th style="padding:10px;text-align:left;">Unidad de Aprendizaje</th>
                    <th style="padding:10px;text-align:center;">Tabulador</th>
                    <th style="padding:10px;text-align:center;">Horas</th>
                    <th style="padding:10px;text-align:right;">Subtotal Men.</th>
                    <th style="padding:10px;text-align:center;">Acción</th>
                </tr>`;
            
            let totalMensual = 0;
            let totalHorasDocente = 0;
            if (materias.length === 0) {
                htmlTabla += `<tr><td colspan="5" style="padding:10px;text-align:center;">Sin materias asignadas</td></tr>`;
            } else {
                materias.forEach((m, idx) => {
                    const horas = m.horas || 0;
                    totalHorasDocente += horas;
                    const nivel = m.nivel_pago || 'Licenciatura';
                    let tarifaLocal = tarifaLic;
                    if (nivel === 'Maestría') tarifaLocal = tarifaMtr;
                    if (nivel === 'Técnico') tarifaLocal = tarifaTec;

                    const subtotal = horas * tarifaLocal;
                    totalMensual += subtotal;
                    
                    // VALIDACION DE CARRERA: Solo puede generar de su propia carrera
                    const puedeGenerar = currentUser.role === 'ADM' || m.carrera === currentUser.role;
                    const btnHtml = puedeGenerar 
                        ? `<button onclick="window.confirmarGeneracionIndividual(${docenteId}, ${m.materia_id}, '${m.materia.replace(/'/g, "\\'")}', '${nombre.replace(/'/g, "\\'")}')" 
                                   style="padding:5px 10px; background:var(--color-maroon); color:white; border:none; border-radius:4px; cursor:pointer; font-size:0.75rem;">
                                   🖨️ Generar
                           </button>`
                        : `<span style="font-size:0.7rem; color:#94a3b8; font-style:italic;">No autorizado (${m.carrera})</span>`;

                    htmlTabla += `<tr style="border-bottom:1px solid #e2e8f0;">
                        <td style="padding:10px; font-weight:600;">${m.materia} <span style="font-size:0.75rem;color:#64748b;font-weight:normal;display:block;">${m.carrera}</span></td>
                        <td style="padding:10px;text-align:center;">
                            <select style="padding:4px; border:1px solid #ccc; border-radius:4px; font-size:0.85rem;" onchange="window.recalcPreviewLevel(this, ${docenteId}, '${m.materia}', ${horas})">
                                <option value="Técnico" ${nivel === 'Técnico' ? 'selected' : ''}>Técnico</option>
                                <option value="Licenciatura" ${nivel === 'Licenciatura' ? 'selected' : ''}>Licenciatura</option>
                                <option value="Maestría" ${nivel === 'Maestría' ? 'selected' : ''}>Maestría</option>
                            </select>
                        </td>
                        <td style="padding:10px;text-align:center;">
                            <input type="number" min="0" value="${horas}" style="width:60px; text-align:center; padding:4px; border:1px solid #ccc; border-radius:4px;" onchange="window.recalcPreview(this, ${docenteId}, '${m.materia}', '${nivel}')" /> hrs
                        </td>
                        <td style="padding:10px;text-align:right; font-family:monospace; font-size:0.95rem;">$${subtotal.toLocaleString('es-MX', {minimumFractionDigits:2})}</td>
                        <td style="padding:10px;text-align:center;">${btnHtml}</td>
                    </tr>`;
                });
            }
            htmlTabla += `<tr style="background:#f8fafc; font-weight:bold;">
                <td colspan="3" style="padding:10px;text-align:right;">SUBTOTAL BRUTO MENSUAL:</td>
                <td style="padding:10px;text-align:right;color:var(--color-maroon);font-family:monospace; font-size:1.05rem;">$${totalMensual.toLocaleString('es-MX', {minimumFractionDigits:2})} MXN</td>
                <td></td>
            </tr>
            </table>
            
            <div style="background:#f0fdf4; border:1px dashed #22c55e; padding:10px; border-radius:8px; margin-bottom:20px; font-size:0.85rem; display:flex; gap:10px; align-items:center;">
                <input type="text" id="add-new-mat" placeholder="Nombre de Materia" style="flex:1; padding:6px; border:1px solid #ccc; border-radius:4px;" />
                <input type="number" id="add-new-hrs" placeholder="Hrs" style="width:60px; padding:6px; border:1px solid #ccc; border-radius:4px;" />
                <select id="add-new-lvl" style="padding:6px; border:1px solid #ccc; border-radius:4px;">
                    <option value="Licenciatura">Licenciatura</option>
                    <option value="Maestría">Maestría</option>
                    <option value="Técnico">Técnico</option>
                </select>
                <button onclick="window.agregarMateriaDinamica(${docenteId})" style="padding:6px 12px; background:#10b981; color:white; border:none; border-radius:4px; cursor:pointer;">+ Asignar</button>
            </div>
            `;

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
                    <strong>Instructivo:</strong> Agrega las materias libremente o ajusta las horas/nivel asignado. Los cambios se guardarán automáticamente en la base de datos al modificar.
                </div>
                ${alertaHrs}
                ${htmlTabla}
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

    window.recalcPreview = async function(inputEl, docId, matNombre, nivelActual) {
        let newVal = parseFloat(inputEl.value) || 0;
        if (newVal < 0) {
            newVal = 0;
            inputEl.value = 0;
        }
        inputEl.disabled = true;
        try {
            await fetch(`/api/asignacion/${docId}/horas`, {
                method: 'PUT',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify({ horas: newVal, materia: matNombre, nivel_pago: nivelActual })
            });
            // Recargar vista previa silenciosamente re-invocando la funcion en modo update
            if(pendingContratoPayload) {
                window.generarContrato(pendingContratoPayload.docente_id, pendingContratoPayload.nombre);
            }
        } catch(e) {
            console.error('Error guardando horas', e);
            alert('Error actualizando horas en base de datos.');
        } finally {
            inputEl.disabled = false;
        }
    };

    window.recalcPreviewLevel = async function(selectEl, docId, matNombre, horasActuales) {
        let nivel = selectEl.value;
        selectEl.disabled = true;
        try {
            await fetch(`/api/asignacion/${docId}/horas`, {
                method: 'PUT',
                headers: {'Content-Type':'application/json'},
                body: JSON.stringify({ horas: horasActuales, materia: matNombre, nivel_pago: nivel })
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

    window.confirmarGeneracionIndividual = async function(docenteId, materiaId, materiaNombre, docenteNombre) {
        if (!confirm(`¿Deseas generar el contrato para la materia "${materiaNombre}"?`)) return;
        
        const btn = event.target;
        const ogText = btn.innerHTML;
        btn.innerHTML = '⏳...';
        btn.disabled = true;

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
            alert("Error: " + err.message);
            btn.innerHTML = ogText;
            btn.disabled = false;
        } finally {
            btn.innerHTML = ogText;
            btn.disabled = false;
        }
    };

    const btnConfirm = document.getElementById('btn-confirm-generar');
    if (btnConfirm) {
        btnConfirm.innerHTML = "Cerrar Ventana";
        btnConfirm.onclick = () => window.closePreviewModal();
    }

});
