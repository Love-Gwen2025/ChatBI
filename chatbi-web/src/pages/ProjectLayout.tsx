import { useEffect, useState } from 'react';
import { Outlet, useParams, useNavigate } from 'react-router-dom';
import { message } from 'antd';
import { getProject, type Project } from '../api/projectApi';
import { switchProject } from '../api/authApi';
import { useAuthStore } from '../store/authStore';

export default function ProjectLayout() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const setLastLoginProjectId = useAuthStore((s) => s.setLastLoginProjectId);
  const [project, setProject] = useState<(Project & { role: string }) | null>(null);

  useEffect(() => {
    if (!projectId) return;

    const pid = Number(projectId);

    // 通知后端切换项目（更新 Redis Session）
    switchProject(pid)
      .then(() => {
        setLastLoginProjectId(pid);
      })
      .catch(() => {
        message.error('无权访问该项目');
        navigate('/projects', { replace: true });
        return;
      });

    getProject(pid)
      .then((res) => setProject(res.data))
      .catch(() => {
        message.error('无权访问该项目');
        navigate('/projects', { replace: true });
      });
  }, [projectId, navigate, setLastLoginProjectId]);

  if (!project) return null;

  return <Outlet context={{ project }} />;
}
