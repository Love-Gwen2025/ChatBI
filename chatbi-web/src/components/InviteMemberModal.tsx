import { Modal, Form, Input, Select } from 'antd';

interface Props {
  open: boolean;
  loading: boolean;
  onOk: (values: { username: string; role: string }) => void;
  onCancel: () => void;
}

export default function InviteMemberModal({ open, loading, onOk, onCancel }: Props) {
  const [form] = Form.useForm();

  const handleOk = async () => {
    const values = await form.validateFields();
    onOk(values);
    form.resetFields();
  };

  return (
    <Modal
      title="邀请成员"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={loading}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ role: 'MEMBER' }}>
        <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input placeholder="输入要邀请的用户名" />
        </Form.Item>
        <Form.Item name="role" label="角色">
          <Select options={[
            { value: 'MEMBER', label: '成员' },
            { value: 'OWNER', label: '管理员' },
          ]} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
