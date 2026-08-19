import React, { useState, useRef, useEffect } from 'react';
import type { UploadFileItem, FileType } from '../types/upload';
import { envConfig, getFileTypeFromName, formatFileSize, uploadFileToBackend, sendResumeToBackend } from '../services/api';

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  onAttach: (files: UploadFileItem[]) => void;
}

export const UploadModal: React.FC<UploadModalProps> = ({ isOpen, onClose, onAttach }) => {
  const [files, setFiles] = useState<UploadFileItem[]>([]);
  const [jdText, setJdText] = useState<string>('');
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [activeUploads, setActiveUploads] = useState<{ [key: string]: () => void }>({});
  const [previewFile, setPreviewFile] = useState<UploadFileItem | null>(null);
  const [selectedResultFile, setSelectedResultFile] = useState<UploadFileItem | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const filesRef = useRef(files);
  filesRef.current = files;

  // Clean up object URLs on unmount
  useEffect(() => {
    return () => {
      filesRef.current.forEach((file) => {
        if (file.objectUrl) {
          URL.revokeObjectURL(file.objectUrl);
        }
      });
    };
  }, []);

  if (!isOpen) return null;

  const uploadingCount = files.filter((f) => f.status === 'uploading').length;

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      processSelectedFiles(Array.from(e.dataTransfer.files));
    }
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      processSelectedFiles(Array.from(e.target.files));
    }
  };

  const processSelectedFiles = (newFiles: File[]) => {
    const createdItems: UploadFileItem[] = newFiles.map((file, index) => {
      const fileId = `file-${Date.now()}-${index}`;
      const type = getFileTypeFromName(file.name);
      const objectUrl = URL.createObjectURL(file);
      return {
        id: fileId,
        name: file.name,
        size: formatFileSize(file.size),
        rawSizeBytes: file.size,
        type,
        progress: 0,
        timeLeft: 'Starting...',
        status: 'uploading',
        backendStatus: 'idle',
        fileObj: file,
        objectUrl,
      };
    });

    setFiles((prev) => [...createdItems, ...prev]);

    createdItems.forEach((item) => {
      const cancelFn = uploadFileToBackend(
        item,
        (id, progress, timeLeft) => {
          setFiles((prev) =>
            prev.map((f) => (f.id === id ? { ...f, progress, timeLeft } : f))
          );
        },
        (id) => {
          setFiles((prev) =>
            prev.map((f) =>
              f.id === id
                ? { ...f, progress: 100, timeLeft: 'Completed', status: 'completed' }
                : f
            )
          );
        },
        (id, errorMsg) => {
          setFiles((prev) =>
            prev.map((f) =>
              f.id === id ? { ...f, status: 'error', errorMessage: errorMsg } : f
            )
          );
        }
      );

      setActiveUploads((prev) => ({ ...prev, [item.id]: cancelFn }));
    });
  };

  const handleOpenResume = (file: UploadFileItem) => {
    if (file.objectUrl) {
      window.open(file.objectUrl, '_blank');
    } else {
      setPreviewFile(file);
    }
  };

  const handleSendToBackend = async (fileId: string) => {
    const targetFile = files.find((f) => f.id === fileId);
    if (!targetFile) return;

    if (!jdText.trim()) {
      alert('Please enter a Job Description before evaluating ATS Score.');
      return;
    }

    setFiles((prev) =>
      prev.map((f) =>
        f.id === fileId
          ? { ...f, backendStatus: 'sending', errorMessage: undefined }
          : f
      )
    );

    const result = await sendResumeToBackend(targetFile, jdText);

    if (result.success && result.data) {
      setFiles((prev) =>
        prev.map((f) =>
          f.id === fileId
            ? { ...f, backendStatus: 'sent', backendResponse: result.data }
            : f
        )
      );
      // Automatically show evaluation details
      setSelectedResultFile({
        ...targetFile,
        backendStatus: 'sent',
        backendResponse: result.data,
      });
    } else {
      setFiles((prev) =>
        prev.map((f) =>
          f.id === fileId
            ? { ...f, backendStatus: 'error', errorMessage: result.error || 'Failed to process resume' }
            : f
        )
      );
    }
  };

  const handleCancelFile = (fileId: string) => {
    const target = files.find((f) => f.id === fileId);
    if (target?.objectUrl) {
      URL.revokeObjectURL(target.objectUrl);
    }
    if (activeUploads[fileId]) {
      activeUploads[fileId]();
    }
    setFiles((prev) => prev.filter((f) => f.id !== fileId));
  };

  const handleRemoveFile = (fileId: string) => {
    const target = files.find((f) => f.id === fileId);
    if (target?.objectUrl) {
      URL.revokeObjectURL(target.objectUrl);
    }
    if (selectedResultFile?.id === fileId) {
      setSelectedResultFile(null);
    }
    setFiles((prev) => prev.filter((f) => f.id !== fileId));
  };

  const handleAttachSubmit = () => {
    onAttach(files);
    onClose();
  };

  const renderFileIcon = (type: FileType) => {
    if (type === 'img') {
      return (
        <div className="file-icon-box">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <rect width="18" height="18" x="3" y="3" rx="2" ry="2" />
            <circle cx="9" cy="9" r="2" />
            <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21" />
          </svg>
        </div>
      );
    }
    return (
      <div className="file-icon-box">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="16" y1="13" x2="8" y2="13" />
          <line x1="16" y1="17" x2="8" y2="17" />
        </svg>
      </div>
    );
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-container" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="modal-header">
          <div className="header-text-group">
            <h2 className="modal-title">Resume Parser & ATS Evaluator</h2>
            <p className="modal-subtitle">Upload your resume to extract skills and evaluate ATS match against your Target Job Description.</p>
          </div>
          <button className="close-btn" onClick={onClose} aria-label="Close modal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        {/* Job Description Input Section */}
        <div className="jd-section">
          <div className="jd-header">
            <label htmlFor="jd-input" className="jd-label">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
                <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
              </svg>
              Target Job Description (JD):
            </label>
            <span className="jd-hint">Required for ATS Match Scoring</span>
          </div>
          <textarea
            id="jd-input"
            rows={3}
            value={jdText}
            onChange={(e) => setJdText(e.target.value)}
            placeholder="Paste the job description or required skills here..."
            className="jd-textarea"
          />
        </div>

        {/* Drag and Drop Zone */}
        <div
          className={`dropzone-container ${isDragging ? 'dragging' : ''}`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
        >
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileInputChange}
            multiple
            className="hidden-file-input"
            accept={envConfig.allowedFileTypes.join(',')}
          />

          <div className="dropzone-grid-bg" />

          {/* Central Upload Content */}
          <div className="dropzone-center-content">
            <div className="upload-badge-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#7c3aed" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
            </div>
            <div className="dropzone-labels">
              <span className="upload-cta-text">
                <span className="click-to-upload">Click to Upload Resume</span> or drag and drop
              </span>
              <span className="max-size-hint">
                Supported: PDF, DOCX, DOC (Max: {envConfig.maxFileSizeMB} MB)
              </span>
            </div>
          </div>
        </div>

        {/* Uploaded Files Section */}
        <div className="files-section">
          {uploadingCount > 0 && (
            <h3 className="uploading-section-title">
              {uploadingCount} {uploadingCount === 1 ? 'file' : 'files'} uploading...
            </h3>
          )}

          {files.length > 0 && uploadingCount === 0 && (
            <h3 className="uploading-section-title">Uploaded Resumes</h3>
          )}

          <div className="files-list">
            {files.map((file) => (
              <div
                key={file.id}
                className={`file-card ${file.status === 'error' || file.backendStatus === 'error' ? 'error-card' : ''}`}
              >
                <div className="file-card-top">
                  <div className="file-info-group">
                    {renderFileIcon(file.type)}
                    <div className="file-meta">
                      <span className="file-name" title={file.name}>
                        {file.name}
                      </span>
                      <span className="file-details">
                        {file.size} •{' '}
                        {file.errorMessage ? (
                          <span className="error-text">{file.errorMessage}</span>
                        ) : file.status === 'completed' ? (
                          file.backendStatus === 'sent' ? (
                            <span className="success-text">
                              ATS Evaluated: Score {file.backendResponse?.ats_score ?? 'N/A'}%
                            </span>
                          ) : (
                            'Ready to evaluate'
                          )
                        ) : (
                          file.timeLeft
                        )}
                      </span>
                    </div>
                  </div>

                  <div className="file-actions">
                    {file.status === 'uploading' && (
                      <>
                        <span className="percentage-text">{file.progress}%</span>
                        <button
                          className="icon-action-btn"
                          onClick={() => handleCancelFile(file.id)}
                          title="Cancel upload"
                        >
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#475569" strokeWidth="2.2" strokeLinecap="round">
                            <line x1="18" y1="6" x2="6" y2="18" />
                            <line x1="6" y1="6" x2="18" y2="18" />
                          </svg>
                        </button>
                      </>
                    )}

                    {file.status === 'completed' && (
                      <button
                        className="icon-action-btn delete-btn"
                        onClick={() => handleRemoveFile(file.id)}
                        title="Remove file"
                      >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.8" strokeLinecap="round">
                          <polyline points="3 6 5 6 21 6" />
                          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                        </svg>
                      </button>
                    )}
                  </div>
                </div>

                {file.status === 'uploading' && (
                  <div className="progress-track">
                    <div
                      className="progress-fill"
                      style={{ width: `${file.progress}%` }}
                    />
                  </div>
                )}

                {/* Flow Step: Action Buttons after uploading */}
                {file.status === 'completed' && (
                  <div className="resume-flow-options">
                    {/* Option 1: Open/Preview Resume */}
                    <button
                      type="button"
                      className="btn-flow-open"
                      onClick={() => handleOpenResume(file)}
                    >
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                        <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                        <polyline points="15 3 21 3 21 9" />
                        <line x1="10" y1="14" x2="21" y2="3" />
                      </svg>
                      Open Document
                    </button>

                    {/* Option 2: Send to Backend ATS Engine */}
                    <button
                      type="button"
                      className={`btn-flow-backend ${file.backendStatus === 'sent' ? 'sent' : ''}`}
                      onClick={() => handleSendToBackend(file.id)}
                      disabled={file.backendStatus === 'sending'}
                    >
                      {file.backendStatus === 'sending' ? (
                        <>
                          <span className="spinner" />
                          Evaluating ATS...
                        </>
                      ) : file.backendStatus === 'sent' ? (
                        <>
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                            <polyline points="20 6 9 17 4 12" />
                          </svg>
                          Re-evaluate
                        </>
                      ) : (
                        <>
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                            <line x1="22" y1="2" x2="11" y2="13" />
                            <polygon points="22 2 15 22 11 13 2 9 22 2" />
                          </svg>
                          Evaluate ATS Match
                        </>
                      )}
                    </button>

                    {/* Option 3: View ATS Results */}
                    {file.backendResponse && (
                      <button
                        type="button"
                        className="btn-flow-results"
                        onClick={() => setSelectedResultFile(file)}
                      >
                        📊 View Breakdown ({file.backendResponse.ats_score ?? 0}%)
                      </button>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="modal-footer">
          <button type="button" className="btn-cancel" onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-attach"
            onClick={handleAttachSubmit}
            disabled={files.length === 0}
          >
            Done ({files.length})
          </button>
        </div>
      </div>

      {/* ATS Score Result Modal Overlay */}
      {selectedResultFile?.backendResponse && (
        <div className="preview-overlay" onClick={() => setSelectedResultFile(null)}>
          <div className="ats-result-modal" onClick={(e) => e.stopPropagation()}>
            <div className="preview-header">
              <div className="ats-header-title">
                <h3>ATS Match Analysis</h3>
                <span className="ats-filename">{selectedResultFile.name}</span>
              </div>
              <button className="close-btn" onClick={() => setSelectedResultFile(null)}>✕</button>
            </div>
            
            <div className="ats-modal-body">
              {/* Score Display Card */}
              <div className="ats-score-banner">
                <div className="score-circle">
                  <span className="score-value">
                    {selectedResultFile.backendResponse.ats_score ?? 0}%
                  </span>
                  <span className="score-sublabel">Match Score</span>
                </div>
                <div className="score-summary-text">
                  <h4>
                    {(selectedResultFile.backendResponse.ats_score ?? 0) >= 75
                      ? '🎯 Strong Match for this Role!'
                      : (selectedResultFile.backendResponse.ats_score ?? 0) >= 50
                      ? '⚡ Moderate Match'
                      : '⚠️ Low Match / Missing Key Requirements'}
                  </h4>
                  {selectedResultFile.backendResponse.matched_required !== undefined && (
                    <p>
                      Matched <strong>{selectedResultFile.backendResponse.matched_required.length}</strong> of{' '}
                      <strong>
                        {(selectedResultFile.backendResponse.matched_required.length) +
                          (selectedResultFile.backendResponse.missing_required?.length || 0)}
                      </strong> required skills
                    </p>
                  )}
                </div>
              </div>

              {/* Experience Comparison Card */}
              <div className="experience-comparison-card">
                <div className="exp-item">
                  <span className="exp-label">💼 JD Required Experience:</span>
                  <span className="exp-badge exp-required">
                    {selectedResultFile.backendResponse.required_experience &&
                    selectedResultFile.backendResponse.required_experience !== 'Not specified' &&
                    selectedResultFile.backendResponse.required_experience !== '0'
                      ? selectedResultFile.backendResponse.required_experience
                      : selectedResultFile.backendResponse.jd_experience_years && selectedResultFile.backendResponse.jd_experience_years > 0
                      ? `${selectedResultFile.backendResponse.jd_experience_years}+ Years`
                      : '0 Years'}
                  </span>
                </div>
                <div className="exp-item">
                  <span className="exp-label">👤 Candidate Experience:</span>
                  <span className="exp-badge exp-resume">
                    {selectedResultFile.backendResponse.resume_experience &&
                    selectedResultFile.backendResponse.resume_experience !== 'Extracted from profile' &&
                    selectedResultFile.backendResponse.resume_experience !== '0'
                      ? selectedResultFile.backendResponse.resume_experience
                      : '0 Years'}
                  </span>
                </div>
              </div>

              {/* Skills Breakdown Grid */}
              <div className="skills-breakdown-grid">
                {/* Matched Required Skills */}
                <div className="skill-category-card matched">
                  <h5>
                    ✅ Matched Required Skills (
                    {selectedResultFile.backendResponse.matched_required?.length || 0})
                  </h5>
                  <div className="skill-tags">
                    {selectedResultFile.backendResponse.matched_required?.length ? (
                      selectedResultFile.backendResponse.matched_required.map((skill, i) => (
                        <span key={i} className="tag tag-matched">{skill}</span>
                      ))
                    ) : (
                      <span className="empty-tag-hint">No direct required matches found</span>
                    )}
                  </div>
                </div>

                {/* Missing Required Skills */}
                <div className="skill-category-card missing">
                  <h5>
                    ❌ Missing Required Skills (
                    {selectedResultFile.backendResponse.missing_required?.length || 0})
                  </h5>
                  <div className="skill-tags">
                    {selectedResultFile.backendResponse.missing_required?.length ? (
                      selectedResultFile.backendResponse.missing_required.map((skill, i) => (
                        <span key={i} className="tag tag-missing">{skill}</span>
                      ))
                    ) : (
                      <span className="empty-tag-hint">None! All required skills matched! 🎉</span>
                    )}
                  </div>
                </div>

                {/* Matched Preferred Skills */}
                <div className="skill-category-card preferred">
                  <h5>
                    ⭐ Matched Preferred Skills (
                    {selectedResultFile.backendResponse.matched_preferred?.length || 0})
                  </h5>
                  <div className="skill-tags">
                    {selectedResultFile.backendResponse.matched_preferred?.length ? (
                      selectedResultFile.backendResponse.matched_preferred.map((skill, i) => (
                        <span key={i} className="tag tag-preferred">{skill}</span>
                      ))
                    ) : (
                      <span className="empty-tag-hint">No preferred skills matched</span>
                    )}
                  </div>
                </div>

                {/* Missing Preferred Skills */}
                <div className="skill-category-card missing-pref">
                  <h5>
                    💡 Missing Preferred Skills (
                    {selectedResultFile.backendResponse.missing_preferred?.length || 0})
                  </h5>
                  <div className="skill-tags">
                    {selectedResultFile.backendResponse.missing_preferred?.length ? (
                      selectedResultFile.backendResponse.missing_preferred.map((skill, i) => (
                        <span key={i} className="tag tag-missing-pref">{skill}</span>
                      ))
                    ) : (
                      <span className="empty-tag-hint">None</span>
                    )}
                  </div>
                </div>

                {/* All Detected Resume Skills */}
                {selectedResultFile.backendResponse.resume_skills && selectedResultFile.backendResponse.resume_skills.length > 0 && (
                  <div className="skill-category-card resume-all-skills">
                    <h5>
                      📄 All Detected Resume Skills (
                      {selectedResultFile.backendResponse.resume_skills.length})
                    </h5>
                    <div className="skill-tags">
                      {selectedResultFile.backendResponse.resume_skills.map((skill, i) => (
                        <span key={i} className="tag tag-detected">{skill}</span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="preview-footer">
              <button className="btn-attach" onClick={() => setSelectedResultFile(null)}>
                Close Breakdown
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Internal Document Preview Overlay */}
      {previewFile && (
        <div className="preview-overlay" onClick={() => setPreviewFile(null)}>
          <div className="preview-modal" onClick={(e) => e.stopPropagation()}>
            <div className="preview-header">
              <h3>{previewFile.name}</h3>
              <button className="close-btn" onClick={() => setPreviewFile(null)}>✕</button>
            </div>
            <div className="preview-body">
              <p>Previewing: <strong>{previewFile.name}</strong> ({previewFile.size})</p>
              {previewFile.objectUrl ? (
                <iframe src={previewFile.objectUrl} title="Resume Preview" width="100%" height="400px" />
              ) : (
                <div className="preview-placeholder">
                  Resume file loaded and ready for backend parsing.
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
