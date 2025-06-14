# JavaCV 설치 및 설정 가이드

## 1. Gradle 의존성 (권장)

### build.gradle.kts (Kotlin DSL)
```kotlin
dependencies {
    // JavaCV 핵심 라이브러리
    implementation("org.bytedeco:javacv:1.5.9")
    
    // 플랫폼별 네이티브 라이브러리 (모든 플랫폼)
    implementation("org.bytedeco:javacv-platform:1.5.9")
    
    // 또는 특정 플랫폼만 선택 (용량 절약)
    implementation("org.bytedeco:ffmpeg-platform:6.0-1.5.9")
    implementation("org.bytedeco:opencv-platform:4.7.0-1.5.9")
    
    // Windows만
    // implementation("org.bytedeco:ffmpeg:6.0-1.5.9:windows-x86_64")
    // implementation("org.bytedeco:opencv:4.7.0-1.5.9:windows-x86_64")
    
    // Linux만
    // implementation("org.bytedeco:ffmpeg:6.0-1.5.9:linux-x86_64")
    // implementation("org.bytedeco:opencv:4.7.0-1.5.9:linux-x86_64")
    
    // macOS만
    // implementation("org.bytedeco:ffmpeg:6.0-1.5.9:macosx-x86_64")
    // implementation("org.bytedeco:opencv:4.7.0-1.5.9:macosx-x86_64")
}
```

### build.gradle (Groovy)
```groovy
dependencies {
    implementation 'org.bytedeco:javacv:1.5.9'
    implementation 'org.bytedeco:javacv-platform:1.5.9'
    
    // 또는 특정 플랫폼
    implementation 'org.bytedeco:ffmpeg-platform:6.0-1.5.9'
    implementation 'org.bytedeco:opencv-platform:4.7.0-1.5.9'
}
```

## 2. Maven 의존성

### pom.xml
```xml
<dependencies>
    <!-- JavaCV 핵심 -->
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>javacv</artifactId>
        <version>1.5.9</version>
    </dependency>
    
    <!-- 모든 플랫폼 지원 -->
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>javacv-platform</artifactId>
        <version>1.5.9</version>
    </dependency>
    
    <!-- FFmpeg 플랫폼별 -->
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>ffmpeg-platform</artifactId>
        <version>6.0-1.5.9</version>
    </dependency>
    
    <!-- OpenCV 플랫폼별 -->
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>opencv-platform</artifactId>
        <version>4.7.0-1.5.9</version>
    </dependency>
</dependencies>
```

## 3. 경량화된 설정 (특정 플랫폼만)

### Windows 64비트만
```kotlin
dependencies {
    implementation("org.bytedeco:javacv:1.5.9")
    implementation("org.bytedeco:ffmpeg:6.0-1.5.9")
    implementation("org.bytedeco:ffmpeg:6.0-1.5.9:windows-x86_64")
    implementation("org.bytedeco:opencv:4.7.0-1.5.9")
    implementation("org.bytedeco:opencv:4.7.0-1.5.9:windows-x86_64")
}
```

### Linux 64비트만
```kotlin
dependencies {
    implementation("org.bytedeco:javacv:1.5.9")
    implementation("org.bytedeco:ffmpeg:6.0-1.5.9")
    implementation("org.bytedeco:ffmpeg:6.0-1.5.9:linux-x86_64")
    implementation("org.bytedeco:opencv:4.7.0-1.5.9")
    implementation("org.bytedeco:opencv:4.7.0-1.5.9:linux-x86_64")
}
```

## 4. Android 프로젝트 설정

### build.gradle (Module: app)
```groovy
android {
    defaultConfig {
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a'
        }
    }
    
    packagingOptions {
        pickFirst '**/libc++_shared.so'
        pickFirst '**/libjsc.so'
    }
}

dependencies {
    implementation 'org.bytedeco:javacv:1.5.9'
    implementation 'org.bytedeco:ffmpeg:6.0-1.5.9'
    implementation 'org.bytedeco:ffmpeg:6.0-1.5.9:android-arm64'
    implementation 'org.bytedeco:ffmpeg:6.0-1.5.9:android-arm'
}
```

## 5. 라이브러리 크기 비교

| 구성 | 크기 | 설명 |
|------|------|------|
| javacv-platform | ~500MB | 모든 플랫폼 지원 |
| Windows만 | ~150MB | Windows x64만 |
| Linux만 | ~120MB | Linux x64만 |
| Android만 | ~80MB | ARM64/ARMv7만 |
| 최소 구성 | ~50MB | FFmpeg만 |

## 6. 버전 호환성

### 최신 안정 버전 (2024년 기준)
- **JavaCV**: 1.5.9
- **FFmpeg**: 6.0
- **OpenCV**: 4.7.0

### 이전 안정 버전
- **JavaCV**: 1.5.8
- **FFmpeg**: 5.1
- **OpenCV**: 4.6.0

## 7. 추가 구성요소 (선택사항)

### 전체 멀티미디어 지원
```kotlin
dependencies {
    // 핵심
    implementation("org.bytedeco:javacv:1.5.9")
    
    // 비디오/오디오 처리
    implementation("org.bytedeco:ffmpeg-platform:6.0-1.5.9")
    
    // 컴퓨터 비전
    implementation("org.bytedeco:opencv-platform:4.7.0-1.5.9")
    
    // 추가 라이브러리들
    implementation("org.bytedeco:openblas-platform:0.3.21-1.5.8")  // 수학 연산
    implementation("org.bytedeco:leptonica-platform:1.83.1-1.5.9") // 이미지 처리
    implementation("org.bytedeco:tesseract-platform:5.3.0-1.5.9")  // OCR
}
```

## 8. 환경별 주의사항

### Windows
- Visual C++ Redistributable 2015-2022 필요할 수 있음
- PATH에 DLL 경로 추가 불필요 (JavaCV가 자동 처리)

### Linux
- GLIBC 2.17 이상 필요
- 일부 배포판에서 추가 시스템 라이브러리 필요:
```bash
# Ubuntu/Debian
sudo apt-get install libavcodec-dev libavformat-dev

# CentOS/RHEL
sudo yum install ffmpeg-devel
```

### macOS
- macOS 10.14 이상 권장
- Xcode Command Line Tools 필요할 수 있음

## 9. 메모리 최적화 설정

### JVM 옵션
```bash
# 힙 메모리 증가
-Xmx4g -Xms1g

# 네이티브 메모리 최적화
-XX:MaxDirectMemorySize=2g

# GC 최적화
-XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### Gradle JVM 설정
```kotlin
// gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:MaxDirectMemorySize=2g
```

## 10. 문제 해결

### 흔한 오류들

#### UnsatisfiedLinkError
```kotlin
// 해결방법: 플랫폼별 네이티브 라이브러리 추가
implementation("org.bytedeco:ffmpeg:6.0-1.5.9:windows-x86_64")
```

#### OutOfMemoryError
```kotlin
// JVM 힙 메모리 증가
-Xmx8g
```

#### 버전 충돌
```kotlin
// 특정 버전 강제 지정
configurations.all {
    resolutionStrategy.force("org.bytedeco:javacv:1.5.9")
}
```

## 11. 개발 환경 검증

### 설치 확인 코드
```kotlin
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.ffmpeg.global.avutil

fun verifyJavaCV() {
    println("JavaCV 버전: ${org.bytedeco.javacv.Loader.getVersion()}")
    println("FFmpeg 버전: ${avutil.av_version_info()}")
    
    try {
        val grabber = FFmpegFrameGrabber("")
        println("FFmpeg 초기화 성공")
        grabber.release()
    } catch (e: Exception) {
        println("FFmpeg 초기화 실패: ${e.message}")
    }
}
```

## 12. 권장 설정 (RTSP H.264 디코딩용)

```kotlin
dependencies {
    // 최소 필수 구성
    implementation("org.bytedeco:javacv:1.5.9")
    implementation("org.bytedeco:ffmpeg:6.0-1.5.9")
    
    // 플랫폼별 (하나만 선택)
    implementation("org.bytedeco:ffmpeg:6.0-1.5.9:windows-x86_64") // Windows
    // implementation("org.bytedeco:ffmpeg:6.0-1.5.9:linux-x86_64") // Linux
    // implementation("org.bytedeco:ffmpeg:6.0-1.5.9:macosx-x86_64") // macOS
}
```

이 구성으로 약 **150MB** 크기로 RTSP H.264 디코딩에 필요한 모든 기능을 사용할 수 있습니다.