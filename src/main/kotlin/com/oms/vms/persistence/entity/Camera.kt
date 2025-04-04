//package com.oms.vms.persistence.entity
//
//import java.util.*
//
//@Entity(name = "TBL_CMR_INFO")
//class Camera {
//
//    @Column(name = "URL")
//    @Setter
//    private var url: String? = null
//
//    @Column(name = "RTSP_URL")
//    @Setter
//    private var rtspUrl: String? = null
//
//    @Column(name = "VMS_ID")
//    @Setter
//    @ColumnDefault(value = "''")
//    private var vmsId: String? = null
//
//    @Setter
//    @Column(name = "LC_CD", columnDefinition = "number")
//    private var locationCode: Int? = null
//
//    @Setter
//    @Column(name = "LC_NM", columnDefinition = "varchar(150)")
//    private var locationName: String? = null
//
//    @Column(name = "PTZ_POSBL_YN")
//    @Setter
//    private var ptzAble = false
//}