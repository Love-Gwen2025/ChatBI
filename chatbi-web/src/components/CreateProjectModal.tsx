import { Modal, Form, Input } from 'antd';

interface Props {
  open: boolean;
  loading: boolean;
  onOk: (values: { name: string; description?: string; tablePrefix?: string }) => void;
  onCancel: () => void;
}

export default function CreateProjectModal({ open, loading, onOk, onCancel }: Props) {
  const [form] = Form.useForm();

  const handleOk = async () => {
    const values = await form.validateFields();
    onOk(values);
    form.resetFields();
  };

  return (
    <Modal
      title="新建项目"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={loading}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
          <Input placeholder="如：电商数据分析" />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea placeholder="项目描述（可选）" rows={2} />
        </Form.Item>
        <Form.Item name="tablePrefix" label="表前缀" tooltip="导入 schema 时使用的表名前缀，如 demo_">
          <Input placeholder="如：demo_" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
