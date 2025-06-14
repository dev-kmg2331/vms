# SPS/PPS 상세 설명

## SPS (Sequence Parameter Set) - NAL 타입 7

### 역할
- **비디오 시퀀스 전체**에 대한 파라미터를 정의
- 디코더가 비디오를 디코딩하기 위해 **반드시 필요한 정보**
- 보통 스트림 시작 시 또는 해상도/프로파일 변경 시 전송

### 포함 정보
```
- 프로파일 및 레벨 정보 (Baseline, Main, High 등)
- 해상도 (폭, 높이)
- 종횡비 (Aspect Ratio)
- 프레임 레이트 정보
- 색상 공간 정보
- 비트 깊이 (8bit, 10bit 등)
- 참조 프레임 개수
- 디블로킹 필터 설정
```

### 예시 SPS 내용
```
Profile: High (100)
Level: 3.1
Resolution: 1920x1080
Frame Rate: 30fps
Chroma Format: 4:2:0
Bit Depth: 8bit
Reference Frames: 4
```

## PPS (Picture Parameter Set) - NAL 타입 8

### 역할
- **픽처(프레임) 단위**의 파라미터를 정의
- SPS보다 더 세부적인 인코딩 설정
- 각 슬라이스가 참조하는 설정값들

### 포함 정보
```
- 양자화 파라미터 (QP) 설정
- 엔트로피 코딩 방식 (CAVLC/CABAC)
- 디블로킹 필터 상세 설정
- 가중 예측 설정
- 슬라이스 그룹 설정
- 변환 8x8 사용 여부
```

## H.264 스트림에서의 순서

```
[SPS] → [PPS] → [IDR Frame] → [P/B Frames] → [P/B Frames] → ...
   ↓       ↓         ↓
필수설정  상세설정   실제영상
```

### 처리 방법
```kotlin
7 -> { // SPS
    println("SPS received: ${h264Data.size} bytes")
    // SPS를 별도 저장 (디코더 초기화에 필요)
    spsData = h264Data.copyOf()
    // 파일에 저장하거나 디코더에 전달
}

8 -> { // PPS  
    println("PPS received: ${h264Data.size} bytes")
    // PPS를 별도 저장
    ppsData = h264Data.copyOf()
    // SPS와 함께 디코더에 전달
}
```

## 실제 스트리밍에서의 중요성

### 1. **디코더 초기화**
```kotlin
// 디코더는 SPS/PPS 없이는 I-frame도 디코딩할 수 없음
decoder.configure(spsData, ppsData)  // 먼저 설정
decoder.decode(iFrameData)           // 그 다음 디코딩 가능
```

### 2. **파일 저장 시**
```kotlin
// MP4나 다른 컨테이너에 저장할 때도 SPS/PPS가 먼저 필요
fileWriter.writeHeader(spsData, ppsData)  // 헤더에 설정 정보
fileWriter.writeFrame(iFrameData)         // 실제 프레임 데이터
```

### 3. **스트림 재시작**
```kotlin
// 네트워크 끊김 후 재연결 시 SPS/PPS를 다시 요청해야 할 수도 있음
if (connectionLost) {
    requestKeyFrame()  // 서버에 I-frame + SPS/PPS 요청
}
```

## 개선된 코드 예시

```kotlin
class H264StreamProcessor {
    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null
    private var isConfigured = false
    
    fun processNALUnit(h264Data: ByteArray) {
        val nalType = h264Data[4].toInt() and 0x1F
        
        when (nalType) {
            7 -> { // SPS
                spsData = h264Data.copyOf()
                println("SPS received: ${h264Data.size} bytes")
                checkConfiguration()
            }
            
            8 -> { // PPS
                ppsData = h264Data.copyOf()
                println("PPS received: ${h264Data.size} bytes")
                checkConfiguration()
            }
            
            5 -> { // I-frame
                if (isConfigured) {
                    processIFrame(h264Data)
                } else {
                    println("I-frame received but decoder not configured (missing SPS/PPS)")
                }
            }
            
            1 -> { // P-frame
                if (isConfigured) {
                    processPFrame(h264Data)
                } else {
                    println("P-frame received but decoder not configured")
                }
            }
        }
    }
    
    private fun checkConfiguration() {
        if (spsData != null && ppsData != null && !isConfigured) {
            // 디코더 설정
            configureDecoder(spsData!!, ppsData!!)
            isConfigured = true
            println("Decoder configured with SPS/PPS")
        }
    }
}
```

## 디버깅에 유용한 SPS 정보 파싱

```kotlin
fun parseSPSInfo(spsData: ByteArray): SPSInfo? {
    // SPS는 복잡한 구조이므로 간단한 정보만 추출
    if (spsData.size < 8) return null
    
    // NAL start code(4) + NAL header(1) 이후부터 SPS 데이터
    val profile = spsData[5].toInt() and 0xFF
    val level = spsData[7].toInt() and 0xFF
    
    return SPSInfo(
        profile = profile,
        level = level,
        size = spsData.size
    )
}

data class SPSInfo(
    val profile: Int,    // 66=Baseline, 77=Main, 100=High
    val level: Int,      // 30=3.0, 31=3.1, 40=4.0 등
    val size: Int
) {
    fun getProfileName(): String = when(profile) {
        66 -> "Baseline"
        77 -> "Main" 
        100 -> "High"
        else -> "Unknown($profile)"
    }
    
    fun getLevelName(): String = "${level/10}.${level%10}"
}
```

**요약**: SPS/PPS는 H.264 스트림의 "설정 파일"이라고 생각하면 됩니다. 이 정보 없이는 실제 비디오 프레임을 디코딩할 수 없으므로, 단순히 무시하지 말고 **별도 저장하여 디코더 설정에 활용**해야 합니다!****