export type FileType = 'pdf' | 'img' | 'fig' | 'ae' | 'ai' | 'doc' | 'other';

export type FileStatus = 'uploading' | 'completed' | 'error';

export type BackendStatus = 'idle' | 'sending' | 'sent' | 'error';

export interface UploadFileItem {
  id: string;
  name: string;
  size: string;
  rawSizeBytes: number;
  type: FileType;
  progress: number;
  timeLeft: string;
  status: FileStatus;
  backendStatus?: BackendStatus;
  backendResponse?: unknown;
  errorMessage?: string;
  fileObj?: File;
  objectUrl?: string;
}

export interface EnvironmentConfig {
  apiBaseUrl: string;
  uploadEndpoint: string;
  maxFileSizeMB: number;
  allowedFileTypes: string[];
  apiKey?: string;
}
