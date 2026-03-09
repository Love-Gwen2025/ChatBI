import ReactECharts from 'echarts-for-react';
import { Alert } from 'antd';

interface Props {
  optionJson: string;
}

export default function ChartRenderer({ optionJson }: Props) {
  const normalizedOption = optionJson.trim();

  try {
    const option = JSON.parse(normalizedOption);
    return (
      <div style={{ margin: '8px 0' }}>
        <ReactECharts option={option} style={{ height: 320 }} notMerge lazyUpdate />
      </div>
    );
  } catch {
    return <Alert type="warning" message="图表配置解析失败" description={normalizedOption} showIcon />;
  }
}

