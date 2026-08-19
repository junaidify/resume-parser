export type FileType = 'pdf' | 'img' | 'fig' | 'ae' | 'ai' | 'doc' | 'other';

export type FileStatus = 'uploading' | 'completed' | 'error';

export type BackendStatus = 'idle' | 'sending' | 'sent' | 'error';

export interface AtsResult {
  jd_required_skills?: string[];
  jd_preferred_skills?: string[];
  jd_experience_years?: number;
  required_experience?: string;
  resume_experience?: string;
  resume_skills?: string[];
  matched_required?: string[];
  matched_preferred?: string[];
  missing_required?: string[];
  missing_preferred?: string[];
  ats_score?: number;
}

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
  backendResponse?: AtsResult;
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
