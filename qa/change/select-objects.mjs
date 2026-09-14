/** Exercise the actual multi-category picker, including when a scenario selects one item. */
export async function selectObjects(page, items) {
  await page.getByRole('button',{name:/(多选变更类别与对象|增减变更类别与对象)$/}).click();
  for (const category of [...new Set(items.map(i=>i.category))]) {
    const label=page.locator('.category-checkboxes .ant-checkbox-wrapper').filter({hasText:category});
    if (!await label.locator('input').isChecked()) await label.click();
  }
  for(const item of items){const row=page.locator('.object-choice').filter({hasText:item.label}).first();if(!await row.locator('input').isChecked())await row.locator('.ant-checkbox-wrapper').click();}
  await page.getByRole('button',{name:/^确认选择 \d+ 项$/}).click();
}
export async function selectOneObject(page, label) {
  const category = label.includes('总经费') ? '经费调整' : label.includes('里程碑') ? '里程碑延期'
    : label.includes('交付物') ? '交付物' : label.includes('项目目标') ? '核心指标'
    : label.includes('层级') ? '层级 / 渠道特殊调整' : label.includes('年度') ? '年度任务纠错'
    : label.includes('结束日期') ? '项目周期' : label.includes('付款') ? '付款节点'
    : label.includes('外协') ? '合作 / 外协方' : '基本信息纠错';
  await selectObjects(page,[{category,label}]);
}
