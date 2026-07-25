package com.syy.taskflowinsight.tracking.path;

import java.util.List;

/**
 * canonical路径的单个typed segment。
 *
 * <p>sealed闭集避免扩展实现携带业务对象或回调；所有寻址和排序只能消费稳定文本事实。</p>
 *
 * @since 4.0.0
 */
public sealed interface PathSegment permits PropertySegment, IndexSegment, MapKeySegment, SetMemberSegment,
        EntityKeySegment {

    enum Kind {
        /** Java声明属性形成的命名路径段。 */
        PROPERTY("PROPERTY"),

        /** List或array的非负位置路径段。 */
        INDEX("INDEX"),

        /** Map exact scalar key形成的稳定路径段。 */
        MAP_KEY("MAP_KEY"),

        /** Set exact scalar member形成的稳定路径段。 */
        SET_MEMBER("SET_MEMBER"),

        /** entity声明类型与有序key components形成的路径段。 */
        ENTITY_KEY("ENTITY_KEY");

        /** machine schema固定token；排序和编码不得依赖ordinal。 */
        private final String wireCode;

        Kind(String wireCode) {
            this.wireCode = wireCode;
        }

        /**
         * 返回machine schema固定token，避免enum重命名或ordinal改变path wire。
         *
         * @return stable segment kind token
         */
        public String wireCode() {
            return wireCode;
        }
    }

    Kind kind();

    List<String> canonicalTextFacts();

    default int canonicalFactCost() {
        List<String> facts = canonicalTextFacts();
        int cost = Math.max(0, facts.size() - 1);
        for (String fact : facts) {
            cost = Math.addExact(cost, fact.length());
        }
        return cost;
    }
}
