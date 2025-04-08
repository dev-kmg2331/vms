// API 정의
const apis = [
    {
        name: "모든 VMS 동기화",
        method: "POST",
        endpoint: "/api/v2/vms/sync",
        description: "모든 VMS 시스템의 카메라 데이터를 동기화합니다.",
        pathParams: [],
        queryParams: [],
        requestBody: {} // 빈 요청 본문
    },
    {
        name: "특정 VMS 동기화",
        method: "POST",
        endpoint: "/api/v2/vms/sync/{vmsType}",
        description: "특정 VMS 타입의 카메라 데이터를 동기화합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: {} // 빈 요청 본문
    },
    {
        name: "지원 VMS 유형 조회",
        method: "GET",
        endpoint: "/api/v2/vms/sync/types",
        description: "지원되는 모든 VMS 유형 목록을 조회합니다.",
        pathParams: [],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "VMS RTSP URL 조회",
        method: "GET",
        endpoint: "/api/v2/vms/sync/rtsp/{vmsType}/{id}",
        description: "특정 카메라의 RTSP URL을 조회합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" },
            { name: "id", description: "카메라 ID" }
        ],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "통합 카메라 조회",
        method: "GET",
        endpoint: "/api/vms/cameras",
        description: "통합된 모든 카메라 데이터를 조회합니다.",
        pathParams: [],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "특정 VMS 통합 카메라 조회",
        method: "GET",
        endpoint: "/api/vms/cameras/type/{vmsType}",
        description: "특정 VMS 유형의 통합 카메라 데이터를 조회합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "특정 VMS 카메라 동기화",
        method: "POST",
        endpoint: "/api/vms/cameras/sync/{vmsType}",
        description: "특정 VMS 유형의 카메라 데이터를 동기화합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: {} // 빈 요청 본문
    },
    {
        name: "VMS 필드 구조 분석",
        method: "GET",
        endpoint: "/api/vms/cameras/analyze/{vmsType}",
        description: "VMS 유형의 필드 구조를 분석합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "스키마 확장",
        method: "POST",
        endpoint: "/api/vms/cameras/schema/extend",
        description: "통합 카메라 스키마를 확장합니다.",
        pathParams: [],
        queryParams: [
            { name: "fieldName", description: "필드 이름" },
            { name: "fieldType", description: "필드 타입" }
        ],
        requestBody: null,
        customForm: {
            type: "schemaExtend",
            fields: [
                { name: "fieldName", label: "필드 이름", type: "text", placeholder: "새 필드 이름", required: true },
                { name: "fieldType", label: "필드 타입", type: "select", options: ["String", "Number", "Boolean", "Object", "Array"], required: true }
            ]
        }
    },
    {
        name: "VMS 매핑 규칙 조회",
        method: "GET",
        endpoint: "/api/v2/vms/mappings/{vmsType}",
        description: "특정 VMS 유형의 매핑 규칙을 조회합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "모든 VMS 매핑 규칙 조회",
        method: "GET",
        endpoint: "/api/v2/vms/mappings",
        description: "모든 VMS 유형의 매핑 규칙을 조회합니다.",
        pathParams: [],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "VMS 키값 구조 조회",
        method: "GET",
        endpoint: "/api/v2/vms/mappings/{vmsType}/keys",
        description: "특정 VMS 유형의 키값 구조를 조회합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "변환 규칙 추가",
        method: "POST",
        endpoint: "/api/v2/vms/mappings/{vmsType}/transformation",
        description: "필드 변환 규칙을 추가합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: {
            sourceField: "",
            targetField: "",
            transformationType: "DEFAULT_CONVERSION",
            parameters: {}
        },
        customForm: {
            type: "transformation",
            fields: [
                { name: "sourceField", label: "소스 필드", type: "text", placeholder: "원본 필드 이름", required: true },
                { name: "targetField", label: "대상 필드", type: "text", placeholder: "변환될 필드 이름", required: true },
                {
                    name: "transformationType",
                    label: "변환 유형",
                    type: "select",
                    options: [
                        "DEFAULT_CONVERSION",
                        "BOOLEAN_CONVERSION",
                        "NUMBER_CONVERSION",
                        "STRING_FORMAT",
                        "DATE_FORMAT"
                    ],
                    required: true
                }
            ]
        }
    },
    {
        name: "변환 규칙 삭제",
        method: "DELETE",
        endpoint: "/api/v2/vms/mappings/{vmsType}/transformation/{index}",
        description: "필드 변환 규칙을 삭제합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" },
            { name: "index", description: "변환 규칙 인덱스" }
        ],
        queryParams: [],
        requestBody: null // DELETE 요청은 본문 없음
    },
    {
        name: "필드별 변환 규칙 조회",
        method: "GET",
        endpoint: "/api/v2/vms/mappings/{vmsType}/transformations/field",
        description: "특정 필드에 대한 변환 규칙을 조회합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [
            { name: "field", description: "소스 필드 이름" }
        ],
        requestBody: null // GET 요청은 본문 없음
    },
    {
        name: "매핑 규칙 삭제",
        method: "DELETE",
        endpoint: "/api/v2/vms/mappings/{vmsType}",
        description: "VMS 유형에 대한 매핑 규칙 전체를 삭제합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: null // DELETE 요청은 본문 없음
    },
    {
        name: "매핑 규칙 초기화",
        method: "POST",
        endpoint: "/api/v2/vms/mappings/{vmsType}/reset",
        description: "VMS 유형의 매핑 규칙을 초기 상태로 리셋합니다.",
        pathParams: [
            { name: "vmsType", description: "VMS 타입 (emstone, naiz, dahua 등)" }
        ],
        queryParams: [],
        requestBody: {} // 빈 요청 본문
    },
    {
        name: "지원되는 VMS 유형 조회",
        method: "GET",
        endpoint: "/api/v2/vms/mappings/vms-types",
        description: "지원되는 모든 VMS 유형 목록을 조회합니다.",
        pathParams: [],
        queryParams: [],
        requestBody: null // GET 요청은 본문 없음
    }
];

let currentApi = null;
let paramFieldCount = 0;

// API 목록 초기화
function initializeApiList() {
    const apiItemsContainer = document.getElementById('apiItems');

    apis.forEach((api, index) => {
        const apiItem = document.createElement('div');
        apiItem.className = 'api-item';
        apiItem.innerHTML = `
                    <span class="method ${api.method.toLowerCase()}">${api.method}</span>
                    <span>${api.name}</span>
                `;
        apiItem.onclick = () => selectApi(index);
        apiItemsContainer.appendChild(apiItem);
    });
}

// API 선택
function selectApi(index) {
    currentApi = apis[index];

    // 활성 항목 스타일 업데이트
    const apiItems = document.querySelectorAll('.api-item');
    apiItems.forEach((item, i) => {
        if (i === index) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    // API 세부 정보 업데이트
    document.getElementById('apiTitle').textContent = currentApi.name;
    document.getElementById('apiDescription').textContent = currentApi.description;
    document.getElementById('apiEndpoint').innerHTML = `<span class="method ${currentApi.method.toLowerCase()}">${currentApi.method}</span> <code>${currentApi.endpoint}</code>`;

    // 경로 파라미터 폼 생성
    createPathParamsForm();

    // 쿼리 파라미터 폼 생성
    createQueryParamsForm();

    // 요청 본문 폼 생성
    createRequestBodyForm();

    // 응답 데이터 초기화
    document.getElementById('responseData').textContent = '아직 응답이 없습니다.';

    // 요청 탭으로 전환
    changeTab('request');
}

// 경로 파라미터 폼 생성
function createPathParamsForm() {
    const container = document.getElementById('pathParamsForm');
    container.innerHTML = '<h4>경로 파라미터</h4>';

    if (currentApi.pathParams.length === 0) {
        container.innerHTML += '<p>경로 파라미터가 없습니다.</p>';
        return;
    }

    currentApi.pathParams.forEach(param => {
        const formRow = document.createElement('div');
        formRow.className = 'form-row';
        formRow.innerHTML = `
                    <label for="path_${param.name}">${param.name}</label>
                    <input 
                        type="text" 
                        id="path_${param.name}" 
                        placeholder="${param.name}" 
                        data-param="${param.name}" 
                        title="${param.description}"
                    >
                    <div class="hint">${param.description}</div>
                `;
        container.appendChild(formRow);
    });
}

// 쿼리 파라미터 폼 생성
function createQueryParamsForm() {
    const container = document.getElementById('queryParamsForm');
    container.innerHTML = '<h4>쿼리 파라미터</h4>';

    if (currentApi.queryParams.length === 0) {
        container.innerHTML += '<p>쿼리 파라미터가 없습니다.</p>';
        return;
    }

    currentApi.queryParams.forEach(param => {
        const formRow = document.createElement('div');
        formRow.className = 'form-row';
        formRow.innerHTML = `
                    <label for="query_${param.name}">${param.name}</label>
                    <input 
                        type="text" 
                        id="query_${param.name}" 
                        placeholder="${param.name}" 
                        data-param="${param.name}" 
                        title="${param.description}"
                    >
                    <div class="hint">${param.description}</div>
                `;
        container.appendChild(formRow);
    });
}

// 요청 본문 폼 생성
function createRequestBodyForm() {
    const container = document.getElementById('requestBodyForm');

    // 요청 본문이 필요하지 않은 경우
    if (currentApi.requestBody === null) {
        container.classList.add('hidden');
        return;
    } else {
        container.classList.remove('hidden');
    }

    container.innerHTML = '<h4>요청 본문</h4>';

    // 사용자 정의 폼이 있는 경우
    if (currentApi.customForm) {
        createCustomForm(container);
        return;
    }

    // 기본 JSON 입력 폼
    const formRow = document.createElement('div');
    formRow.className = 'form-row';
    formRow.innerHTML = `
                <textarea id="requestBodyJson" placeholder="JSON 형식의 요청 본문을 입력하세요">${JSON.stringify(currentApi.requestBody, null, 2)}</textarea>
            `;
    container.appendChild(formRow);
}

// 사용자 정의 폼 생성
function createCustomForm(container) {
    const customForm = currentApi.customForm;

    switch (customForm.type) {
        case 'schemaExtend':
            createSchemaExtendForm(container, customForm);
            break;
        case 'transformation':
            createTransformationForm(container, customForm);
            break;
        default:
            // 알 수 없는 폼 유형일 경우 기본 JSON 입력으로 돌아감
            container.innerHTML += `
                        <textarea id="requestBodyJson" placeholder="JSON 형식의 요청 본문을 입력하세요">${JSON.stringify(currentApi.requestBody, null, 2)}</textarea>
                    `;
    }
}

// 스키마 확장 폼 생성
function createSchemaExtendForm(container, formDef) {
    const formContainer = document.createElement('div');
    formContainer.className = 'form-grid';

    formDef.fields.forEach(field => {
        const formRow = document.createElement('div');
        formRow.className = 'form-row';

        if (field.type === 'select') {
            let optionsHtml = '';
            field.options.forEach(option => {
                optionsHtml += `<option value="${option}">${option}</option>`;
            });

            formRow.innerHTML = `
                        <label for="schema_${field.name}">${field.label}</label>
                        <select 
                            id="schema_${field.name}" 
                            data-field="${field.name}" 
                            ${field.required ? 'required' : ''}
                        >
                            <option value="">-- 선택 --</option>
                            ${optionsHtml}
                        </select>
                    `;
        } else {
            formRow.innerHTML = `
                        <label for="schema_${field.name}">${field.label}</label>
                        <input 
                            type="${field.type}" 
                            id="schema_${field.name}" 
                            placeholder="${field.placeholder || field.label}" 
                            data-field="${field.name}" 
                            ${field.required ? 'required' : ''}
                        >
                    `;
        }

        formContainer.appendChild(formRow);
    });

    container.appendChild(formContainer);
}

// 변환 규칙 폼 생성
function createTransformationForm(container, formDef) {
    const formContainer = document.createElement('div');

    // 기본 필드 생성
    formDef.fields.forEach(field => {
        const formRow = document.createElement('div');
        formRow.className = 'form-row';

        if (field.type === 'select') {
            let optionsHtml = '';
            field.options.forEach(option => {
                optionsHtml += `<option value="${option}">${option}</option>`;
            });

            formRow.innerHTML = `
                        <label for="transform_${field.name}">${field.label}</label>
                        <select 
                            id="transform_${field.name}" 
                            data-field="${field.name}" 
                            ${field.required ? 'required' : ''}
                            ${field.name === 'transformationType' ? 'onchange="handleTransformationTypeChange(this.value)"' : ''}
                        >
                            <option value="">-- 선택 --</option>
                            ${optionsHtml}
                        </select>
                    `;
        } else {
            formRow.innerHTML = `
                        <label for="transform_${field.name}">${field.label}</label>
                        <input 
                            type="${field.type}" 
                            id="transform_${field.name}" 
                            placeholder="${field.placeholder || field.label}" 
                            data-field="${field.name}" 
                            ${field.required ? 'required' : ''}
                        >
                    `;
        }

        formContainer.appendChild(formRow);
    });

    // 파라미터 섹션 (변환 유형에 따라 다름)
    const parametersSection = document.createElement('div');
    parametersSection.id = 'transformationParameters';
    parametersSection.className = 'parameters-container hidden';
    parametersSection.innerHTML = `
                <h4>변환 파라미터</h4>
                <div id="parameterFields"></div>
                <button class="add-param-field" onclick="addParameterField()">+ 파라미터 추가</button>
            `;

    formContainer.appendChild(parametersSection);
    container.appendChild(formContainer);
}

// 변환 유형 변경 핸들러
function handleTransformationTypeChange(value) {
    const parametersSection = document.getElementById('transformationParameters');
    const parameterFields = document.getElementById('parameterFields');

    // 파라미터 필드 초기화
    parameterFields.innerHTML = '';
    paramFieldCount = 0;

    // 특정 변환 유형만 파라미터가 필요
    if (['STRING_FORMAT', 'DATE_FORMAT'].includes(value)) {
        parametersSection.classList.remove('hidden');

        if (value === 'STRING_FORMAT') {
            addParameterField('format', '%s');
        } else if (value === 'DATE_FORMAT') {
            addParameterField('sourceFormat', 'yyyy-MM-dd HH:mm:ss');
            addParameterField('targetFormat', 'yyyy-MM-dd HH:mm:ss');
        }
    } else {
        parametersSection.classList.add('hidden');
    }
}

// 파라미터 필드 추가
function addParameterField(keyValue = '', valueValue = '') {
    const parameterFields = document.getElementById('parameterFields');

    const fieldId = paramFieldCount++;
    const fieldRow = document.createElement('div');
    fieldRow.className = 'param-field';
    fieldRow.id = `param_field_${fieldId}`;
    fieldRow.innerHTML = `
                <input type="text" placeholder="파라미터 키" value="${keyValue}" class="param-key">
                <input type="text" placeholder="파라미터 값" value="${valueValue}" class="param-value">
                <button onclick="removeParameterField(${fieldId})">-</button>
            `;

    parameterFields.appendChild(fieldRow);
}

// 파라미터 필드 제거
function removeParameterField(fieldId) {
    const fieldRow = document.getElementById(`param_field_${fieldId}`);
    if (fieldRow) {
        fieldRow.remove();
    }
}

// 탭 변경
function changeTab(tabName) {
    const tabs = document.querySelectorAll('.tab');
    const tabContents = document.querySelectorAll('.tab-content');

    tabs.forEach(tab => {
        if (tab.textContent.toLowerCase().includes(tabName)) {
            tab.classList.add('active');
        } else {
            tab.classList.remove('active');
        }
    });

    tabContents.forEach(content => {
        if (content.id === `${tabName}Tab`) {
            content.classList.add('active');
        } else {
            content.classList.remove('active');
        }
    });
}

// 요청 데이터 수집
function collectRequestData() {
    if (!currentApi) {
        return null;
    }

    // URL 준비 (경로 파라미터 대체)
    let url = currentApi.endpoint;

    // 경로 파라미터 대체
    if (currentApi.pathParams.length > 0) {
        currentApi.pathParams.forEach(param => {
            const inputEl = document.getElementById(`path_${param.name}`);
            if (inputEl && inputEl.value.trim()) {
                url = url.replace(`{${param.name}}`, inputEl.value.trim());
            }
        });
    }

    // 쿼리 파라미터 추가
    const queryParams = [];
    if (currentApi.queryParams.length > 0) {
        currentApi.queryParams.forEach(param => {
            const inputEl = document.getElementById(`query_${param.name}`);
            if (inputEl && inputEl.value.trim()) {
                queryParams.push(`${encodeURIComponent(param.name)}=${encodeURIComponent(inputEl.value.trim())}`);
            }
        });
    }

    if (queryParams.length > 0) {
        url += `?${queryParams.join('&')}`;
    }

    // 요청 본문 준비
    let requestBody = null;
    if (['POST', 'PUT', 'PATCH'].includes(currentApi.method) && currentApi.requestBody !== null) {
        // 사용자 정의 폼이 있는 경우
        if (currentApi.customForm) {
            requestBody = collectCustomFormData();
        } else {
            // 일반 JSON 텍스트 영역에서 수집
            const bodyInput = document.getElementById('requestBodyJson').value.trim();
            if (bodyInput) {
                try {
                    requestBody = JSON.parse(bodyInput);
                } catch (error) {
                    alert(`JSON 파싱 오류: ${error.message}`);
                    return null;
                }
            } else {
                requestBody = currentApi.requestBody; // 기본값 사용
            }
        }
    }

    url = "http://localhost:8080" + url

    return { url, requestBody };
}

// 사용자 정의 폼 데이터 수집
function collectCustomFormData() {
    const customForm = currentApi.customForm;

    switch (customForm.type) {
        case 'schemaExtend':
            return collectSchemaExtendFormData();
        case 'transformation':
            return collectTransformationFormData();
        default:
            return {};
    }
}

// 스키마 확장 폼 데이터 수집
function collectSchemaExtendFormData() {
    // 스키마 확장 API는 쿼리 파라미터로 전송하므로 본문 불필요
    return {};
}

// 변환 규칙 폼 데이터 수집
function collectTransformationFormData() {
    const data = {};

    // 기본 필드 수집
    currentApi.customForm.fields.forEach(field => {
        const inputEl = document.getElementById(`transform_${field.name}`);
        if (inputEl) {
            data[field.name] = inputEl.value;
        }
    });

    // 파라미터 수집
    const parameterFields = document.querySelectorAll('.param-field');
    const parameters = {};

    parameterFields.forEach(field => {
        const keyInput = field.querySelector('.param-key');
        const valueInput = field.querySelector('.param-value');

        if (keyInput && valueInput && keyInput.value.trim()) {
            parameters[keyInput.value.trim()] = valueInput.value.trim();
        }
    });

    data.parameters = parameters;

    return data;
}

// API 요청 전송
function sendRequest() {
    if (!currentApi) {
        alert('API를 선택해주세요.');
        return;
    }

    const sendButton = document.getElementById('sendButton');
    sendButton.disabled = true;
    sendButton.textContent = '요청 중...';

    const requestData = collectRequestData();
    if (!requestData) {
        sendButton.disabled = false;
        sendButton.textContent = '요청 보내기';
        return;
    }

    const { url, requestBody } = requestData;

    // 요청 옵션 준비
    const options = {
        method: currentApi.method,
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'Origin': 'http://localhost:8080'
        }
    };

    if (requestBody && Object.keys(requestBody).length > 0) {
        options.body = JSON.stringify(requestBody);
    }

    // 요청 보내기
    fetch(url, options)
        .then(response => {
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                return response.json().then(data => ({
                    status: response.status,
                    statusText: response.statusText,
                    data
                }));
            } else {
                return response.text().then(text => ({
                    status: response.status,
                    statusText: response.statusText,
                    data: text
                }));
            }
        })
        .then(result => {
            const responseData = document.getElementById('responseData');
            const resultStr = typeof result.data === 'object' ?
                JSON.stringify(result.data, null, 2) :
                result.data;

            responseData.innerHTML = `Status: ${result.status} ${result.statusText}\n\n${resultStr}`;

            // 응답 탭으로 전환
            changeTab('response');
        })
        .catch(error => {
            const responseData = document.getElementById('responseData');
            responseData.textContent = `오류 발생: ${error.message}`;

            // 응답 탭으로 전환
            changeTab('response');
        })
        .finally(() => {
            sendButton.disabled = false;
            sendButton.textContent = '요청 보내기';
        });
}

// 페이지 로드 시 초기화
window.onload = function () {
    initializeApiList();
};