package com.core.microservices.core.material.services;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.core.api.core.material.MaterialService;
import com.core.api.core.material.ReportDTO;
import com.core.api.core.payload.FileUploadResponse;
import com.core.microservices.core.material.persistence.MaterialEntity;
import com.core.microservices.core.material.persistence.MaterialRepository;
import com.core.util.http.ServiceUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class MaterialServiceImpl implements MaterialService {

    private static final Logger LOG = LoggerFactory.getLogger(MaterialServiceImpl.class);

    private final MaterialRepository repository;

    private final MaterialMapper mapper;

    private final ServiceUtil serviceUtil;

    private final MaterialUploadService service;

    private final S3UploadService s3service;

    @Autowired
    public MaterialServiceImpl(MaterialRepository repository, MaterialMapper mapper, ServiceUtil serviceUtil,
        MaterialUploadService service, S3UploadService s3service) {
        this.repository = repository;
        this.mapper = mapper;
        this.serviceUtil = serviceUtil;
        this.service = service;
        this.s3service = s3service;
    }

    @Override
    public FileUploadResponse sendMaterial(MultipartFile file, String webUrl, int userId) {
        //have to add try catch later
        System.out.println(webUrl + " : " + userId);
        String fileName = service.storeFile(file);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/downloadFile/")
            .path(fileName)
            .toUriString();

        //현재 시간 가져오기
        DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
        LocalDateTime time = LocalDateTime.now();
        String time_now = time.format (formatter);

        // 받은 multipartFile s3에 upload
        String fileurl = s3service.uploadMaterial(file, time_now, "core", userId);

        //  filename, 시간, fromId 항상 1, toId = userId, approval default값, clarity null값, webUrl를 이용해서
        //  db 저장
        MaterialEntity entity =  mapper.dtoToEntity(fileurl, 1, userId, "미승인", time, webUrl);
        repository.save(entity);

        return new FileUploadResponse(fileName, fileDownloadUri, file.getContentType(), file.getSize());
    }

    @Override
    public FileUploadResponse sendMaterial1(MultipartFile file) {
        //have to add try catch later
        System.out.println(file.getName());
        String fileName = service.storeFile(file);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/downloadFile/")
            .path(fileName)
            .toUriString();

        return new FileUploadResponse(fileName, fileDownloadUri, file.getContentType(), file.getSize());
    }

    @Override
    public List<ReportDTO> getReportList(int userId) {

        int toUserId = userId;

        List<MaterialEntity> reportList = repository.findAllByToId(toUserId);
        List<ReportDTO> reportDtoList = new ArrayList<>(); // 후에 mapper로 처리

        for(MaterialEntity entity : reportList){
            reportDtoList.add(convertReportEntityToDto(entity));
        }
        return reportDtoList;
    }

    private ReportDTO convertReportEntityToDto(MaterialEntity entity) {

        String cdnFileName = "https://" + s3service.CLOUD_FRONT_DOMAIN_NAME + "/" + entity.getFileName();
        return new ReportDTO(cdnFileName,entity.getFromId(), entity.getToId(),entity.getApproval(), entity.getCreateTime(), entity.getWebUrl());

    }

}
