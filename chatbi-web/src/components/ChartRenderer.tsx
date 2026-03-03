import ReactECharts from 'echarts-for-react';
import { Alert } from 'antd';

interface Props {
  optionJson: string;
}

export default function ChartRenderer({ optionJson }: Props) {
  try {
    const option = JSON.parse(optionJson);
    return (
      <div style={{ margin: '8px 0' }}>
        <ReactECharts option={option} style={{ height: 300 }} />
      </div>
    );
  } catch {
    return <Alert type="warning" message="图表配置解析失败" description={optionJson} />;
  }
}
