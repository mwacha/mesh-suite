package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.CorEstampaRequest;
import com.meshsuite.produto.dto.CorEstampaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CorEstampaService {

    private final CorEstampaRepository corEstampaRepository;
    private final ProdutoRepository produtoRepository;

    public CorEstampaService(CorEstampaRepository corEstampaRepository, ProdutoRepository produtoRepository) {
        this.corEstampaRepository = corEstampaRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<CorEstampaResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<CorEstampa> spec = Specification.allOf(
                CorEstampaSpecifications.comBusca(busca),
                CorEstampaSpecifications.comAtivo(ativo));
        Page<CorEstampa> pagina = corEstampaRepository.findAll(spec, pageable);

        List<UUID> ids = pagina.getContent().stream().map(CorEstampa::getId).toList();
        Map<UUID, Long> contagens = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByCorEstampaIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProdutoRepository.CorEstampaProdutoCount::getCorEstampaId,
                                ProdutoRepository.CorEstampaProdutoCount::getTotal));

        return pagina.map(corEstampa -> toResponse(corEstampa, contagens.getOrDefault(corEstampa.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public CorEstampaResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public CorEstampaResponse criar(UUID tenantId, CorEstampaRequest request) {
        validarNome(request.nome(), null);

        CorEstampa corEstampa = new CorEstampa();
        corEstampa.setTenantId(tenantId);
        aplicar(corEstampa, request);
        return toResponse(corEstampaRepository.saveAndFlush(corEstampa));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public CorEstampaResponse atualizar(UUID id, CorEstampaRequest request) {
        validarNome(request.nome(), id);

        CorEstampa corEstampa = buscarEntidadePorId(id);
        aplicar(corEstampa, request);
        return toResponse(corEstampaRepository.saveAndFlush(corEstampa));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        CorEstampa corEstampa = buscarEntidadePorId(id);
        long vinculados = produtoRepository.countByCorEstampaId(id);
        if (vinculados > 0) {
            throw new CorEstampaEmUsoException(vinculados);
        }
        corEstampaRepository.delete(corEstampa);
    }

    private CorEstampa buscarEntidadePorId(UUID id) {
        return corEstampaRepository.findById(id).orElseThrow(CorEstampaNaoEncontradaException::new);
    }

    private void validarNome(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? corEstampaRepository.existsByNome(nome)
                : corEstampaRepository.existsByNomeAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new CorEstampaNomeDuplicadoException();
        }
    }

    private void aplicar(CorEstampa corEstampa, CorEstampaRequest request) {
        corEstampa.setNome(request.nome());
        corEstampa.setDataVigencia(request.dataVigencia());
        corEstampa.setDescricao(request.descricao());
        corEstampa.setAtivo(request.ativo() != null ? request.ativo() : true);
    }

    private CorEstampaResponse toResponse(CorEstampa corEstampa) {
        return toResponse(corEstampa, produtoRepository.countByCorEstampaId(corEstampa.getId()));
    }

    private CorEstampaResponse toResponse(CorEstampa corEstampa, long produtosVinculados) {
        return new CorEstampaResponse(
                corEstampa.getId(), corEstampa.getNome(), corEstampa.getDataVigencia(), corEstampa.getDescricao(),
                corEstampa.getAtivo(), produtosVinculados, corEstampa.getCriadoEm());
    }
}
