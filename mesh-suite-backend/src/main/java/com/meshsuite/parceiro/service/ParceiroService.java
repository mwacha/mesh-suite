package com.meshsuite.parceiro.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.ParceiroContato;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import com.meshsuite.parceiro.dto.*;
import com.meshsuite.parceiro.exception.DocumentoDuplicadoException;
import com.meshsuite.parceiro.exception.ParceiroNaoEncontradoException;
import com.meshsuite.parceiro.exception.ParceiroValidacaoException;
import com.meshsuite.parceiro.repository.ParceiroRepository;
import com.meshsuite.parceiro.repository.specification.ParceiroSpecifications;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParceiroService {

    private final ParceiroRepository parceiroRepository;

    public ParceiroService(ParceiroRepository parceiroRepository) {
        this.parceiroRepository = parceiroRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public Page<ParceiroSummaryResponse> listar(String busca, List<StatusParceiro> status, List<TipoPessoa> tipoDocumento,
                                                 String documento, List<String> uf, List<String> cidade,
                                                 PapelParceiro papel, Pageable pageable) {
        Specification<Parceiro> spec = Specification.allOf(
                ParceiroSpecifications.comBusca(busca),
                ParceiroSpecifications.comStatus(status),
                ParceiroSpecifications.comTipoPessoa(tipoDocumento),
                ParceiroSpecifications.comDocumento(documento),
                ParceiroSpecifications.comUf(uf),
                ParceiroSpecifications.comCidade(cidade),
                ParceiroSpecifications.comPapel(papel));
        return parceiroRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public ParceiroResumoResponse resumo(PapelParceiro papel) {
        long ativos = contarPorStatus(StatusParceiro.ATIVO, papel);
        long emRisco = contarPorStatus(StatusParceiro.EM_RISCO, papel);
        long bloqueados = contarPorStatus(StatusParceiro.BLOQUEADO, papel);
        return new ParceiroResumoResponse(ativos + emRisco + bloqueados, ativos, emRisco, bloqueados);
    }

    private long contarPorStatus(StatusParceiro status, PapelParceiro papel) {
        return papel == null
                ? parceiroRepository.countByStatus(status)
                : parceiroRepository.countByStatusAndPapeisContaining(status, papel);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.CUSTOMER, action = Action.VIEW)
    public ParceiroResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.CREATE)
    public ParceiroResponse criar(UUID tenantId, ParceiroRequest request) {
        validar(request, null);

        Parceiro parceiro = new Parceiro();
        parceiro.setTenantId(tenantId);
        aplicar(parceiro, request);
        return toResponse(parceiroRepository.saveAndFlush(parceiro));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public ParceiroResponse atualizar(UUID id, ParceiroRequest request) {
        validar(request, id);

        Parceiro parceiro = buscarEntidadePorId(id);
        aplicar(parceiro, request);
        return toResponse(parceiroRepository.saveAndFlush(parceiro));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.EDIT)
    public ParceiroResponse atualizarStatus(UUID id, StatusParceiro novoStatus) {
        if (novoStatus != StatusParceiro.ATIVO && novoStatus != StatusParceiro.BLOQUEADO) {
            throw new ParceiroValidacaoException("Só é possível definir o status como ATIVO ou BLOQUEADO manualmente");
        }
        Parceiro parceiro = buscarEntidadePorId(id);
        parceiro.setStatus(novoStatus);
        return toResponse(parceiroRepository.saveAndFlush(parceiro));
    }

    @Transactional
    @RequiresPermission(module = Module.CUSTOMER, action = Action.DELETE)
    public void excluir(UUID id) {
        parceiroRepository.delete(buscarEntidadePorId(id));
    }

    private Parceiro buscarEntidadePorId(UUID id) {
        return parceiroRepository.findById(id).orElseThrow(ParceiroNaoEncontradoException::new);
    }

    private void validar(ParceiroRequest request, UUID idAtual) {
        boolean semPapelAtivo = request.papeis().stream()
                .noneMatch(p -> p == PapelParceiro.CLIENTE || p == PapelParceiro.FORNECEDOR);
        if (semPapelAtivo) {
            throw new ParceiroValidacaoException("Selecione ao menos o papel Cliente ou Fornecedor");
        }

        String documento = normalizarDocumento(request.documento());
        int tamanhoEsperado = request.tipoPessoa() == TipoPessoa.FISICA ? 11 : 14;
        if (documento.length() != tamanhoEsperado) {
            throw new ParceiroValidacaoException(
                    request.tipoPessoa() == TipoPessoa.FISICA
                            ? "CPF deve ter 11 dígitos"
                            : "CNPJ deve ter 14 dígitos");
        }

        boolean duplicado = idAtual == null
                ? parceiroRepository.existsByDocumento(documento)
                : parceiroRepository.existsByDocumentoAndIdNot(documento, idAtual);
        if (duplicado) {
            throw new DocumentoDuplicadoException();
        }
    }

    // Aceita CNPJ/CPF digitados ou colados com a máscara usual (pontos, barra,
    // hífen) -- só os dígitos são validados e persistidos.
    private static String normalizarDocumento(String documento) {
        return documento.replaceAll("\\D", "");
    }

    private void aplicar(Parceiro parceiro, ParceiroRequest request) {
        parceiro.setTipoPessoa(request.tipoPessoa());
        parceiro.setDocumento(normalizarDocumento(request.documento()));
        parceiro.setNomeFantasia(request.nomeFantasia());
        parceiro.setRazaoSocial(request.razaoSocial());
        parceiro.setPapeis(new HashSet<>(request.papeis()));
        parceiro.setEmailsCobranca(request.emailsCobranca());
        parceiro.setWhatsapp(request.whatsapp());
        parceiro.setIndicadorIe(request.indicadorIe());
        parceiro.setInscricaoEstadual(request.inscricaoEstadual());
        parceiro.setInscricaoMunicipal(request.inscricaoMunicipal());
        parceiro.setInscricaoSuframa(request.inscricaoSuframa());
        parceiro.setCep(request.cep());
        parceiro.setLogradouro(request.logradouro());
        parceiro.setNumero(request.numero());
        parceiro.setBairro(request.bairro());
        parceiro.setComplemento(request.complemento());
        parceiro.setUf(request.uf());
        parceiro.setCidade(request.cidade());
        parceiro.setObservacao(request.observacao());

        parceiro.getContatos().clear();
        List<ParceiroContatoDto> contatos = request.contatos() == null ? List.of() : request.contatos();
        for (ParceiroContatoDto dto : contatos) {
            ParceiroContato contato = new ParceiroContato();
            contato.setParceiro(parceiro);
            contato.setNome(dto.nome());
            contato.setEmail(dto.email());
            contato.setTelefoneComercial(dto.telefoneComercial());
            contato.setTelefoneCelular(dto.telefoneCelular());
            contato.setCargo(dto.cargo());
            parceiro.getContatos().add(contato);
        }
    }

    private ParceiroSummaryResponse toSummary(Parceiro p) {
        return new ParceiroSummaryResponse(
                p.getId(), p.getNomeFantasia(), p.getRazaoSocial(), p.getDocumento(), p.getTipoPessoa(),
                p.getCidade(), p.getUf(), p.getWhatsapp(), p.getStatus());
    }

    private ParceiroResponse toResponse(Parceiro p) {
        List<ParceiroContatoDto> contatos = p.getContatos().stream()
                .map(c -> new ParceiroContatoDto(c.getNome(), c.getEmail(), c.getTelefoneComercial(),
                        c.getTelefoneCelular(), c.getCargo()))
                .toList();
        return new ParceiroResponse(
                p.getId(), p.getTipoPessoa(), p.getDocumento(), p.getNomeFantasia(), p.getRazaoSocial(),
                p.getStatus(), p.getPapeis(), p.getEmailsCobranca(), p.getWhatsapp(), p.getIndicadorIe(),
                p.getInscricaoEstadual(), p.getInscricaoMunicipal(), p.getInscricaoSuframa(), p.getCep(),
                p.getLogradouro(), p.getNumero(), p.getBairro(), p.getComplemento(), p.getUf(), p.getCidade(),
                p.getObservacao(), contatos);
    }
}
