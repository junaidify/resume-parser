import { useState } from 'react';
import { UploadModal } from './components/UploadModal';
import type { UploadFileItem } from './types/upload';
import './App.css';

export function App() {
  const [isModalOpen, setIsModalOpen] = useState<boolean>(true);
  const [attachedFiles, setAttachedFiles] = useState<UploadFileItem[]>([]);

  const handleAttachFiles = (files: UploadFileItem[]) => {
    setAttachedFiles(files);
  };

  return (
    <div className="app-viewport">
      {!isModalOpen && (
        <button
          onClick={() => setIsModalOpen(true)}
          className="btn-reopen"
        >
          Open Upload Modal
        </button>
      )}

      {attachedFiles.length > 0 && !isModalOpen && (
        <div className="attached-summary">
          <h3>Attached Files ({attachedFiles.length})</h3>
          <ul>
            {attachedFiles.map((file) => (
              <li key={file.id}>
                {file.name} - {file.size}
              </li>
            ))}
          </ul>
        </div>
      )}

      <UploadModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onAttach={handleAttachFiles}
      />
    </div>
  );
}

export default App;
