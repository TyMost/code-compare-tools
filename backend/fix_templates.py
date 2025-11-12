from pathlib import Path
import re

path = Path('backend/src/main/java/com/example/migratediff/application/GenerateAppService.java')
text = path.read_text(encoding='utf-8')

insert_block = """    private static final String INSERT_TEMPLATE = join(
            "/** 迁移生成的代码片段开始",
            " * 请按照 project_rules.md 修改适配",
            " */",
            "${deltaO}",
            "/** 迁移生成的代码片段结束 */"
    );

"""

update_block = """    private static final String UPDATE_TEMPLATE = join(
            "/** 迁移适配开始",
            " * 请按照 project_rules.md 修改适配",
            " */",
            "/** 目标仓库原实现开始 */",
            "${deltaG}",
            "/** 目标仓库原实现结束 */",
            "/** 迁移适配建议开始 */",
            "${deltaO}",
            "/** 迁移适配建议结束 */",
            "/** 迁移适配段结束 */"
    );

"""

delete_line = "    private static final String DELETE_HINT_TEMPLATE = \"// TODO: 请手动删除目标仓库中的对应实现\";\n\n"

text = re.sub(r"    private static final String INSERT_TEMPLATE = join\([\s\S]+?\n    \);\n\n", insert_block, text, count=1)
text = re.sub(r"    private static final String UPDATE_TEMPLATE = join\([\s\S]+?\n    \);\n\n", update_block, text, count=1)
text = re.sub(r"    private static final String DELETE_HINT_TEMPLATE = .*?;\n\n", delete_line, text, count=1)

path.write_text(text, encoding='utf-8')
