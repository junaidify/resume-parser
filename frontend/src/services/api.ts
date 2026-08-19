import type { AtsResult, EnvironmentConfig, UploadFileItem } from '../types/upload';

// Read configuration from environment variables (.env)
export const envConfig: EnvironmentConfig = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
  uploadEndpoint: import.meta.env.VITE_UPLOAD_ENDPOINT || '/parse/resume',
  maxFileSizeMB: Number(import.meta.env.VITE_MAX_FILE_SIZE_MB) || 10,
  allowedFileTypes: (import.meta.env.VITE_ALLOWED_FILE_TYPES || '.pdf,.doc,.docx')
    .split(',')
    .map((t: string) => t.trim().toLowerCase()),
  apiKey: import.meta.env.VITE_API_KEY || '',
};

/**
 * Utility to format bytes into readable strings (e.g. 32.9 MB)
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

/**
 * Detect file type category from filename extension
 */
export function getFileTypeFromName(filename: string): UploadFileItem['type'] {
  const ext = filename.split('.').pop()?.toLowerCase() || '';
  if (ext === 'pdf') return 'pdf';
  if (['png', 'jpg', 'jpeg', 'webp', 'gif', 'img', 'svg'].includes(ext)) return 'img';
  if (ext === 'fig') return 'fig';
  if (ext === 'ae') return 'ae';
  if (ext === 'ai') return 'ai';
  if (['doc', 'docx', 'txt'].includes(ext)) return 'doc';
  return 'other';
}

/**
 * Simulates or executes initial file selection / staging
 */
export function uploadFileToBackend(
  fileItem: UploadFileItem,
  onProgress: (fileId: string, progress: number, timeLeft: string) => void,
  onComplete: (fileId: string) => void,
  onError: (fileId: string, error: string) => void
): () => void {
  let isCancelled = false;

  const maxBytes = envConfig.maxFileSizeMB * 1024 * 1024;
  if (fileItem.rawSizeBytes > maxBytes) {
    setTimeout(() => {
      onError(fileItem.id, `File size exceeds maximum limit of ${envConfig.maxFileSizeMB} MB`);
    }, 300);
    return () => {};
  }

  let progress = fileItem.progress || 0;
  const interval = setInterval(() => {
    if (isCancelled) return;

    progress += Math.floor(Math.random() * 25) + 15;
    if (progress >= 100) {
      progress = 100;
      clearInterval(interval);
      onProgress(fileItem.id, 100, 'Ready');
      onComplete(fileItem.id);
    } else {
      onProgress(fileItem.id, progress, 'Uploading...');
    }
  }, 100);

  return () => {
    isCancelled = true;
    clearInterval(interval);
  };
}

/**
 * Sends the uploaded resume and job description to the Spring Boot backend API
 */
export async function sendResumeToBackend(
  fileItem: UploadFileItem,
  jdText: string
): Promise<{ success: boolean; data?: AtsResult; error?: string }> {
  const targetUrl = `${envConfig.apiBaseUrl}${envConfig.uploadEndpoint}`;

  try {
    if (!fileItem.fileObj) {
      throw new Error('No valid document file provided.');
    }

    const actualJdText = jdText ? jdText.trim() : '';
    if (!actualJdText) {
      throw new Error('Please enter a Job Description (JD) to evaluate ATS match score.');
    }

    const formData = new FormData();
    formData.append('file', fileItem.fileObj);
    formData.append('jdText', actualJdText);

    const headers: Record<string, string> = {};
    if (envConfig.apiKey) {
      headers['Authorization'] = `Bearer ${envConfig.apiKey}`;
    }

    const response = await fetch(targetUrl, {
      method: 'POST',
      headers,
      body: formData,
    });

    const responseData = await response.json().catch(() => null);

    if (!response.ok) {
      const errorMsg =
        responseData?.message ||
        responseData?.error ||
        `Server error: HTTP ${response.status} (${response.statusText})`;
      throw new Error(errorMsg);
    }

    // Spring returns either parsed JSON object or stringified JSON
    const parsedData: AtsResult =
      typeof responseData === 'string' ? JSON.parse(responseData) : responseData;

    return { success: true, data: parsedData };
  } catch (err: unknown) {
    let errorMsg = err instanceof Error ? err.message : 'Upload and ATS evaluation failed';
    if (errorMsg === 'Failed to fetch' || errorMsg.includes('NetworkError') || errorMsg.includes('Load failed')) {
      errorMsg = `Cannot connect to Backend API at ${targetUrl}. Please ensure Spring Boot is running (port 8081). Run: cd resume-parser && ./gradlew bootRun`;
    }
    console.error(`Error sending resume to ${targetUrl}:`, err);
    return {
      success: false,
      error: errorMsg,
    };
  }
}
